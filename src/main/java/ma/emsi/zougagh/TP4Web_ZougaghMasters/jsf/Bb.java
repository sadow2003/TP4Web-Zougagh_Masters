package ma.emsi.zougagh.TP4Web_ZougaghMasters.jsf; // Votre package

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import ma.emsi.zougagh.TP4Web_ZougaghMasters.Models.Assistant; // Votre Assistant.java
import ma.emsi.zougagh.TP4Web_ZougaghMasters.services.RagService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SessionScoped
public class Bb implements Serializable {

    @Inject
    private RagService ragService; // Injection du service RAG

    private Assistant assistant;
    private ChatMemory chatMemory; // Garde l'historique de la session

    // --- Propriétés pour index.xhtml (TP2) ---
    private String question = "";
    private String reponse = "";
    private String conversation = "";
    private String roleSysteme = "Tu es un assistant utile"; // Rôle par défaut
    private boolean roleSystemeChangeable = true;
    private List<SelectItem> listeRolesSysteme;

    @PostConstruct
    public void init() {
        // Vérifie si le service RAG a démarré
        if (ragService.getAnswerModel() == null || ragService.getRetrievalAugmentor() == null) {
            this.reponse = "ERREUR: Le service RAG n'a pas pu démarrer. Vérifiez les logs du serveur et les clés API (Gemini/Tavily).";
            return;
        }

        // Crée une mémoire de chat POUR CETTE SESSION
        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // Construit l'assistant pour cette session
        reconstruireAssistant();
    }

    // Construit l'assistant RAG
    private void reconstruireAssistant() {
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(ragService.getAnswerModel())
                .retrievalAugmentor(ragService.getRetrievalAugmentor())
                .chatMemory(this.chatMemory)
                .systemMessageProvider(this.roleSysteme)
                .build();
    }

    /**
     * C'est la fonction que votre index.xhtml appelle.
     * C'est ici qu'on met la logique du TP4.
     */
    public String envoyer() {
        if (question == null || question.trim().isEmpty()) {
            this.reponse = "Veuillez poser une question.";
            return null; // Reste sur la même page
        }

        try {
            // Verrouille le rôle système après la 1ère question
            this.roleSystemeChangeable = false;

            // Appelle l'assistant RAG (logique TP4)
            String reponseDuChat = assistant.chat(question);

            // Met à jour les champs de l'interface (logique TP2)
            this.reponse = reponseDuChat;
            this.conversation += "Question : \n" + question + "\n\n";
            this.conversation += "Réponse : \n" + reponseDuChat + "\n\n----------\n\n";

            // Vide la question pour la prochaine saisie
            this.question = "";

        } catch (Exception e) {
            e.printStackTrace();
            this.reponse = "Une erreur est survenue : " + e.getMessage();
        }

        return null; // Reste sur la même page
    }

    /**
     * Action pour le bouton "Nouveau chat" de votre index.xhtml
     */
    public String nouveauChat() {
        this.question = "";
        this.reponse = "";
        this.conversation = "";
        this.roleSystemeChangeable = true; // Permet de changer le rôle
        this.chatMemory.clear(); // Vide la mémoire de LangChain4j
        reconstruireAssistant(); // Reconstruit l'assistant avec le nouveau rôle (s'il a changé)
        return null;
    }

    // --- Getters et Setters pour index.xhtml (TP2) ---

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }

    public String getConversation() { return conversation; }
    public void setConversation(String conversation) { this.conversation = conversation; }

    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }

    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }

    // Pour la liste déroulante de votre index.xhtml
    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            // Génère les rôles de l'API prédéfinis
            this.listeRolesSysteme = new ArrayList<>();
            // Vous pouvez évidemment écrire ces rôles dans la langue que vous voulez.
            String role = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """;

            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            role = """
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user type a French text, you translate it into English.
                    If the user type an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais-Français"));

            role = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town
                    are you tell them the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide touristique"));

            role = """
                    You are a motivating fitness coach.
                    Give the user simple workout advice or healthy eating tips.
                    Keep your answers short and encouraging.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Coach Sportif"));
        }

        return this.listeRolesSysteme;
    }
}