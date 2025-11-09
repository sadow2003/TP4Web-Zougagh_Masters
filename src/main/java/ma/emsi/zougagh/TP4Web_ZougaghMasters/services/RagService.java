package ma.emsi.zougagh.TP4Web_ZougaghMasters.services;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named
@ApplicationScoped // Un seul bean pour toute l'application
public class RagService {

    // Ces objets sont "lourds" et partagés
    private ChatModel answerModel;
    private RetrievalAugmentor retrievalAugmentor;

    @PostConstruct
    public void init() {
        System.out.println("INFO: Démarrage de RagService... Initialisation...");

        // Test 2: Activation du Logging
        configureLogger();

        // --- 1. Configuration des Clés et Modèles ---
        String geminiApiKey = System.getenv("TP2_ZougaghMounsif");
        String tavilyApiKey = System.getenv("TP4-Masters");

        if (geminiApiKey == null || tavilyApiKey == null) {
            System.err.println("ERREUR: Clés API (Gemini ou Tavily) non définies !");
            return;
        }

        ChatModel routingModel = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.0)
                .logRequestsAndResponses(true)
                .build();

        this.answerModel = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // --- 2. Ingestion des 2 PDF ---
        System.out.println("INFO: Ingestion du PDF 1 (IA)...");
        EmbeddingStore<TextSegment> storeIA = ingestDocument(toPath("agentsmcp.pdf"), embeddingModel);

        System.out.println("INFO: Ingestion du PDF 2 (Entreprise)...");
        EmbeddingStore<TextSegment> storeEntreprise = ingestDocument(toPath("entrepreneur.pdf"), embeddingModel);

        // --- 3. Création des Retrievers (PDFs + Web) ---
        ContentRetriever retrieverIA = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(storeIA)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .build();

        ContentRetriever retrieverEntreprise = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(storeEntreprise)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .build();

        // Test 5: Retriever Web
        WebSearchEngine webSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(tavilyApiKey)
                .build();
        ContentRetriever retrieverWeb = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .maxResults(2)
                .build();

        // --- 4. Configuration du Routage (Test 3 + Test 5) ---
        Map<ContentRetriever, String> retrieverMap = new HashMap<>();
        retrieverMap.put(retrieverIA, "Informations sur l'intelligence artificielle, les agents, et le RAG");
        retrieverMap.put(retrieverEntreprise, "Informations sur l'entrepreneuriat, la création d'entreprise, le business");
        retrieverMap.put(retrieverWeb, "Actualités, météo, informations générales, ou tout autre sujet");

        QueryRouter queryRouter = new LanguageModelQueryRouter(routingModel, retrieverMap);

        // --- 5. Stockage des composants finaux ---
        this.retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();

        System.out.println("INFO: RagService est prêt !");
    }

    // --- Méthodes "Helper" (copiées des tests) ---
    private static void configureLogger() {
        Logger packageLogger = Logger.getLogger("dev.langchain4j");
        packageLogger.setLevel(Level.FINE);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINE);
        packageLogger.addHandler(handler);
    }
    private Path toPath(String relativePath) {
        try {
            URL fileUrl = RagService.class.getClassLoader().getResource(relativePath);
            if (fileUrl == null) throw new RuntimeException("Resource not found: " + relativePath);
            return Paths.get(fileUrl.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    private EmbeddingStore<TextSegment> ingestDocument(Path documentPath, EmbeddingModel embeddingModel) {
        DocumentParser parser = new ApacheTikaDocumentParser();
        Document document = FileSystemDocumentLoader.loadDocument(documentPath, parser);
        List<TextSegment> segments = DocumentSplitters.recursive(300, 30).split(document);
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);
        return embeddingStore;
    }

    // --- Getters (pour que ChatBean puisse y accéder) ---
    public ChatModel getAnswerModel() { return answerModel; }
    public RetrievalAugmentor getRetrievalAugmentor() { return retrievalAugmentor; }
}