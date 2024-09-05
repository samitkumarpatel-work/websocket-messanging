package com.example.websocket_messanging;

import com.sun.security.auth.UserPrincipal;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@Slf4j
public class WebsocketMessangingApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebsocketMessangingApplication.class, args);
	}

	@Bean
	Map<String, Map<String, String>> tempStorage() {
		return new ConcurrentHashMap<>();
	}

	@EventListener
	public void onSessionConnectEvent(SessionConnectEvent event) {
		event.getMessage().getHeaders().forEach((k, v) -> log.info("###### SessionConnectEvent ## {}: {}", k, v));

		//Mapping stomp connect headers uId with sessionId and uuId (customise Principle name) 1 = [sessionId, uuId]
		var uuId = event.getUser().getName();
		var sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
		var nativeHeaders = event.getMessage().getHeaders().get("nativeHeaders", Map.class);
		if (nativeHeaders != null && nativeHeaders.containsKey("uId")) {
			var uId = ((List<String>) nativeHeaders.get("uId")).getFirst();
			log.info("###### uId {} = [uuId={}, sessionId={}]", uId, uuId, sessionId);
			tempStorage().put(uId, Map.of("uuId", uuId, "sessionId", sessionId));
		}

		log.info("###### tempStorage {}", tempStorage());
	}

}

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/topic", "/queue");
		config.setApplicationDestinationPrefixes("/app");
		config.setUserDestinationPrefix("/user");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				.setAllowedOriginPatterns("*")
				.setHandshakeHandler(new DefaultHandshakeHandler() {
					@Override
					protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
						//Prepare a UniqueId, so that It can be mapped to a user during SessionConnectEvent
						String uuId = UUID.randomUUID().toString();
						attributes.put("uuId", uuId);
						return new UserPrincipal(uuId);
					}
				})
				.withSockJS();
	}
}

@Data
@Builder
class Message {
	private String sender;
	private String recipient;
	private String content;
}

@Controller
class MessageController {

	private final SimpMessagingTemplate messagingTemplate;
	@Autowired
	Map<String, Map<String, String>> tempStorage;

	public MessageController(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/message")
	public void sendMessage(@Payload Message message, Principal principal) {
		//{ 'auto-delete': true, 'x-message-ttl': 6000, id: uId}
		System.out.println(principal);
		messagingTemplate.convertAndSendToUser(tempStorage.get(message.getRecipient()).getOrDefault("uuId","error") , "/queue/messages", message);
	}
}