(ns skypeclj.skype
  (:use camel-snake-kebab)
  (:require [clojure.tools.logging :as log]
            [clojure.reflect :as r])
  (:import [com.skype.api Skype Account Conversation Message Conversation$ListType
            Conversation$ParticipantFilter Message$Type SkypeListener ContactGroup
            Skype$ProxyType Skype$QualityTestType ContactListener ConversationListener
            MessageListener ParticipantListener AccountListener ContactGroupListener
            ContactSearchListener SmsListener TransferListener VideoListener
            VoicemailListener Account$Status]
           [com.skype.ipc ClientConfiguration ConnectionListener]
           java.io.IOException))

(set! *warn-on-reflection* true)

(def listeners (atom {}))

(defn post-text
  [^Conversation conversation message]
  (log/info "post-text")
  (when (not (nil? conversation))
    (.postText conversation message false)))

(defn log-message
  [^Message message]
  (log/info "log-message")
  (let [author (.getAuthorDisplayName message)
        ^Message$Type message-type (.getType message)]
    ;; TODO implement other message types too
    (condp = message-type
      Message$Type/POSTED_TEXT
      (log/info (format "author '%s' posted '%s'" author (.getBodyXml message)))
      (log/info "no matching type found"))))

(defn get-participants
  [^Conversation conversation]
  (.getParticipants conversation Conversation$ParticipantFilter/ALL))

(defn open-conversation
  [^Skype skype user]
  (log/info "open-conversation")
  (let [conversation (.getConversationByParticipants
                      skype (into-array [user]) true false)]
    ;; TODO obviously remove this
    (post-text conversation "Helga hier!")
    (doseq [m (.unconsumedMessages (.getLastMessages conversation 0))]
      (log-message m))
    conversation))

(defn log-conversation-lists
  [& conversation-lists]
  (doseq [conversations conversation-lists]
    (do
      (log/info "printing...")
      (doseq [^Conversation conversation conversations]
        (let [key (.getOid conversation)
              display-name (.getDisplayName conversation)
              identity (.getIdentity conversation)]
          (log/info (format "conversation key: %s display-name: %s identity: %s" key display-name identity)))))))

;; Conversation conversation = skClient.skype.getConversationByParticipants(names.toArray(new String[names.size()]), true, false);
(defn get-conversation-list
  [^Skype skype]
  (log/info "get-conversation-list")
  (let [conversations (.getConversationList skype Conversation$ListType/ALL_CONVERSATIONS)
        really-all-conversations (.getConversationList skype Conversation$ListType/REALLY_ALL_CONVERSATIONS)
        inbox-conversations (.getConversationList skype Conversation$ListType/INBOX_CONVERSATIONS)
        live-conversations (.getConversationList skype Conversation$ListType/LIVE_CONVERSATIONS)
        bookmarked-conversations (.getConversationList skype Conversation$ListType/BOOKMARKED_CONVERSATIONS)]
    ;; (log/info "currently in" (count conversations) "conversation(s)")
    (log/info (format "c: %s r: %s %s %s %s" conversations really-all-conversations inbox-conversations live-conversations bookmarked-conversations))
    (log-conversation-lists conversations really-all-conversations inbox-conversations live-conversations bookmarked-conversations)))

(defn login
  [^Skype skype username password]
  (log/info "login")
  (let [account (.getAccount skype username)]
    (.loginWithPassword account password false false)
    account))

(defn logout
  [^Skype skype username]
  (log/info "logout")
  (let [account (.getAccount skype username)]
    (.logout account (Boolean/valueOf false))))

(defn logged-in?
  [^Account account]
  (log/info "logged-in?" account)
  (if (not (nil? account))
    (let [status-with-progress (.getStatusWithProgress account)]
      (if (and (not (nil? status-with-progress))
               (= Account$Status/LOGGED_IN (.status status-with-progress)))
        true))
    false))

(defn add-listener!
  [type method handler]
  (swap! listeners assoc-in [type method] handler)
  nil)

(defn remove-listener!
  [type method]
  (swap! listeners update-in [type] dissoc [method])
  nil)

(defn call
  [listener method & args]
  (log/info "call" listener method)
  (when-let [f (get-in @listeners [listener method])]
    (apply f args)))

(defmacro deflistener
  [interface]
  (let [members (:members (r/reflect (resolve interface)))
        methods (for [member members]
                  (let [method-name (:name member)
                        arg-count (count (:parameter-types member))
                        args (repeatedly arg-count gensym)
                        listener (keyword (->kebab-case interface))
                        method (keyword (->kebab-case method-name))]
                    `(~method-name ~(vec (cons (gensym "this") args))
                                   (call ~listener ~method ~@args))))]
    `(reify ~interface ~@methods)))

;; (clojure.pprint/pprint (macroexpand '(deflistener ConnectionListener)))

(defmacro def-property-listener
  [listener-class argument-names & body]
  (let [[this object property value string-value] argument-names]
    `(reify ~listener-class
       (~'onPropertyChange
         [~this ~object ~property ~value ~string-value]
         ~@body))))

(defn register-default-listeners
  [^Skype skype]
  (doto skype
    (.registerSkypeListener (deflistener SkypeListener))
    (.registerConversationListener (deflistener ConversationListener))
    (.registerMessageListener (deflistener MessageListener))
    (.registerParticipantListener (deflistener ParticipantListener))
    (.registerAccountListener (deflistener AccountListener))
    (.registerContactListener (deflistener ContactListener))
    (.registerContactGroupListener (deflistener ContactGroupListener))
    (.registerContactSearchListener (deflistener ContactSearchListener))
    (.registerSmsListener (deflistener SmsListener))
    (.registerTransferListener (deflistener TransferListener))
    (.registerVideoListener (deflistener VideoListener))
    (.registerVoicemailListener (deflistener VoicemailListener))))

(defn deregister-all-listeners
  [^Skype skype]
  (doto skype
    (.unRegisterConnectionListener nil)
    (.unRegisterSkypeListener nil)
    (.unRegisterConversationListener nil)
    (.unRegisterMessageListener nil)
    (.unRegisterParticipantListener nil)
    (.unRegisterAccountListener nil)
    (.unRegisterContactListener nil)
    (.unRegisterContactGroupListener nil)
    (.unRegisterContactSearchListener nil)
    (.unRegisterSmsListener nil)
    (.unRegisterTransferListener nil)
    (.unRegisterVideoListener nil)
    (.unRegisterVoicemailListener nil)))

(defn connect
  [key-filename host port]
  (log/info "connect")
  (let [skype (Skype.)
        client-configuration (ClientConfiguration.)]
    (doto client-configuration
      (.setTcpTransport host port)
      (.setCertificate key-filename))
    (.init skype client-configuration (deflistener ConnectionListener))
    (register-default-listeners skype)
    ;; TODO retry on failure
    (try
      (.start skype)
      (log/info "connected")
      skype
      (catch Exception e
        (log/info "connect exception" e)
        nil))))

(defn disconnect
  [^Skype skype]
  (log/info "disconnect")
  ;; (let [account (.getAccount skype bot-name)]
  ;;   (log/info "account" account)
  ;;   (when (logged-in? account)
  ;;     (do
  ;;       (log/info "disconnect - we're logged in")
  ;;       (.logout account true))))
  (deregister-all-listeners skype)
  (.stop skype))

(defn start
  [key-filename runtime-host runtime-port]
  (log/info "start")
  (connect key-filename runtime-host runtime-port))

(defn stop
  [^Skype skype]
  (log/info "stop")
  (disconnect skype))
