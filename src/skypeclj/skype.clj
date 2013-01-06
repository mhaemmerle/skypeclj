(ns skypeclj.skype
  (:require [clojure.tools.logging :as log]
            [skypeclj.config :as config])
  (:import [com.skype.api Skype Account Conversation Message Conversation$ListType
            Conversation$ParticipantFilter Message$Type SkypeListener ContactGroup
            Skype$ProxyType Skype$QualityTestType ContactListener ConversationListener
            MessageListener ParticipantListener AccountListener ContactGroupListener
            ContactSearchListener SmsListener TransferListener VideoListener
            VoicemailListener Account$Status]
           [com.skype.ipc ClientConfiguration ConnectionListener]
           java.io.IOException))

(set! *warn-on-reflection* true)

(def bot-name (:username config/config))

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

(defn get-conversation-list
  [^Skype skype]
  (log/info "get-conversation-list")
  (let [conversations (.getConversationList skype Conversation$ListType/ALL_CONVERSATIONS)]
    (doseq [^Conversation conversation conversations]
      (let [key (.getOid conversation)
            display-name (.getDisplayName conversation)
            identity (.getIdentity conversation)]
        (log/info (format "conversation key: %s display-name: %s identity: %s" key display-name identity))))))

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

(defn create-connection-listener
  []
  (reify ConnectionListener
    (sidOnConnecting [this]
      (log/info "sid-on-connecting"))
    (sidOnConnected [this]
      (log/info "sid-on-connected"))
    (sidOnDisconnected [this cause]
      (log/info "sid-on-disconnected" cause))))

(defn create-skype-listener
  []
  (reify SkypeListener
    (onNewCustomContactGroup
      [this object group]
      (log/info "skype-listener/onNewCustomContactGroup"))
    (onContactOnlineAppearance
      [this object contact]
      (log/info "skype-listener/onContactOnlineAppearance" (.getSkypeName contact)))
    (onContactGoneOffline
      [this object contact]
      (log/info "skype-listener/onContactGoneOffline" (.getSkypeName contact)))
    (onConversationListChange
      [this object conversation type added]
      (log/info "skype-listener/onConversationListChange"))
    (onMessage
      [this object message changes-inbox-timestamp
       supersedes-history-message conversation]
      (log-message message))
    (onProxyAuthFailure
      [this object type]
      (log/info "skype-listener/onProxyAuthFailure"))
    (onH264Activated
      [this object]
      (log/info "skype-listener/onH264Activated"))
    (onQualityTestResult
      [this object test-type test-result with-user details xml-details]
      (log/info "skype-listener/onQualityTestResult"))
    (onApp2AppDatagram
      [this object appname stream data]
      (log/info "skype-listener/onApp2AppDatagram"))
    (onApp2AppStreamListChange
      [this object appname list-type streams received-sizes]
      (log/info "skype-listener/onApp2AppStreamListChange"))))

(defn create-conversation-listener
  []
  (reify ConversationListener
    (onPropertyChange
      [this participant property value string-value]
      (log/info "conversation-listener/onPropertyChange"))
    (onParticipantListChange
      [this conversation]
      (log/info "conversation-listener/onParticipantListChange"))
    (onMessage
      [this conversation message]
      (log/info "conversation-listener/onMessage"))
    (onSpawnConference
      [this conversation spawned-conversation]
      (log/info "conversation-listener/onSpawnConference"))))

(defn register-listeners!
  [^Skype skype]
  (doto skype
    (.registerSkypeListener (create-skype-listener))
    (.registerConversationListener (create-conversation-listener))
    (.registerMessageListener (reify MessageListener
                                (onPropertyChange
                                  [this message property value string-value]
                                  (log/info "message-listener/onPropertyChange"))))
    (.registerParticipantListener (reify ParticipantListener
                                    (onPropertyChange
                                      [this participant property value string-value]
                                      (log/info "participant-listener/onPropertyChange"))
                                    (onIncomingDtmf
                                      [this participant dtmf]
                                      (log/info "participant-listener/onIncomingDtmf"))
                                    (onLiveSessionVideosChanged
                                      [this participant]
                                      (log/info "participant-listener/onLiveSessionVideosChanged"))))
    (.registerAccountListener (reify AccountListener
                                (onPropertyChange
                                  [this account property value string-value]
                                  (log/info "account-listener/onPropertyChange"))))
    (.registerContactListener (reify ContactListener
                                (onPropertyChange
                                  [this object property value svalue]
                                  (log/info "contact-listener/onPropertyChange"))))
    (.registerContactGroupListener (reify ContactGroupListener
                                     (onPropertyChange
                                       [this contact-group property value string-value]
                                       (log/info "contact-group-listener/onPropertyChange"))
                                     (onChangeConversation
                                       [this contact-group conversation]
                                       (log/info "contact-group-listener/onChangeConversation"))
                                     (onChange
                                       [this contact-group contact]
                                       (log/info "contact-group-listener/onChange"))))
    (.registerContactSearchListener (reify ContactSearchListener
                                      (onPropertyChange
                                        [this contact-search property value string-value]
                                        (log/info "contact-search-listener/onPropertyChange"))
                                      (onNewResult
                                        [this contact-search contact rank-value]
                                        (log/info "contact-search-listener/onNewResult"))))
    (.registerSmsListener (reify SmsListener
                            (onPropertyChange
                              [this sms property value string-value]
                              (log/info "sms-listener/onPropertyChange"))))
    (.registerTransferListener (reify TransferListener
                                 (onPropertyChange
                                   [this transfer property value string-value]
                                   (log/info "transfer-listener/onPropertyChange"))))
    (.registerVideoListener (reify VideoListener
                              (onPropertyChange
                                [this video property value string-value]
                                (log/info "video-listener/onPropertyChange"))))
    (.registerVoicemailListener (reify VoicemailListener
                                  (onPropertyChange
                                    [this voicemail property value string-value]
                                    (log/info "voicemail-listener/onPropertyChange"))))))

(defn deregister-listeners!
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
  [key-filename skypekit-host skypekit-port]
  (log/info "connect")
  (let [skype (Skype.)
        client-configuration (ClientConfiguration.)]
    (doto client-configuration
      (.setTcpTransport skypekit-host skypekit-port)
      (.setCertificate key-filename))
    (.init skype client-configuration (create-connection-listener))
    (register-listeners! skype)
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
  (deregister-listeners! skype)
  (.stop skype))

(defn start
  [key-filename runtime-host runtime-port]
  (log/info "start")
  (connect key-filename runtime-host runtime-port))

(defn stop
  [^Skype skype]
  (log/info "stop")
  (disconnect skype))
