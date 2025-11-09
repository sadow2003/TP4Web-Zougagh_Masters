package ma.emsi.zougagh.TP4Web_ZougaghMasters.jsf;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
// Assurez-vous que les imports pointent vers vos classes
import ma.emsi.zougagh.TP4Web_ZougaghMasters.Models.Assistant;
import ma.emsi.zougagh.TP4Web_ZougaghMasters.Models.ChatMessage;
import ma.emsi.zougagh.TP4Web_ZougaghMasters.services.RagService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// Le nom que votre index.xhtml utilise
@SessionScoped
public class Bb implements Serializable {

    // NOUVEAU: Injection du service applicatif
    @Inject
    private RagService ragService;

    // INCHANGÉ (propriétés publiques pour JSF)
    private String message;
    private List<ChatMessage> conversation = new ArrayList<>();

    // NOUVEAU: L'assistant (privé)
    private Assistant assistant;

    @PostConstruct
    public void init() {
        // MODIFIÉ: Logique d'initialisation

        // 1. Créer une mémoire de chat POUR CETTE SESSION
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);

        // 2. Construire un Assistant lié à cette session
        if (ragService.getAnswerModel() != null && ragService.getRetrievalAugmentor() != null) {

            this.assistant = AiServices.builder(Assistant.class)
                    .chatModel(ragService.getAnswerModel()) // Utilise le modèle partagé
                    .retrievalAugmentor(ragService.getRetrievalAugmentor()) // Utilise le RAG partagé
                    .chatMemory(chatMemory) // Utilise la mémoire DE CETTE SESSION
                    .build();

            conversation.add(new ChatMessage("Assistant", "Bonjour ! Je suis prêt. Posez-moi des questions sur l'IA, l'entrepreneuriat, ou sur le Web."));
        } else {
            conversation.add(new ChatMessage("Erreur", "Le service RAG n'a pas pu démarrer. Vérifiez les logs et les clés API (Gemini/Tavily)."));
        }
    }

    // MODIFIÉ: Logique interne
    public void sendMessage() {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        try {
            // INCHANGÉ (du point de vue de JSF)
            conversation.add(new ChatMessage("Vous", message));

            // MODIFIÉ (logique interne)
            // Au lieu d'appeler Gemini directement, on appelle l'assistant RAG
            String response = assistant.chat(message);

            // INCHANGÉ (du point de vue de JSF)
            conversation.add(new ChatMessage("Assistant", response));

        } catch (Exception e) {
            conversation.add(new ChatMessage("Erreur", e.getMessage()));
            e.printStackTrace();
        }
        message = ""; // Vider le champ
    }

    // --- Getters et Setters (INCHANGÉS) ---
    // (index.xhtml a besoin de ceux-ci)

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<ChatMessage> getConversation() { return conversation; }
}