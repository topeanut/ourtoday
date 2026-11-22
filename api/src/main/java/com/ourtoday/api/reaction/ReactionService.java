package com.ourtoday.api.reaction;

import com.ourtoday.api.post.PostService;
import com.ourtoday.api.reaction.dto.ReactionToggleResponse;
import com.ourtoday.api.push.event.ReactionAddedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final PostService postService;
    private final ApplicationEventPublisher publisher;

    /**
     * 같은 이모지가 있으면 취소, 없으면 추가
     */
    @Transactional
    public ReactionToggleResponse toggle(UUID userId, UUID postId, EmojiType emojiType) {
        postService.getPostInMyCouple(userId, postId);

        return reactionRepository.findByPostIdAndUserIdAndEmojiType(postId, userId, emojiType)
            .map(existing -> {
                reactionRepository.delete(existing);
                return new ReactionToggleResponse(false, emojiType);
            })
            .orElseGet(() -> {
                // 동시 중복 추가는 (post_id, user_id, emoji_type) 유니크 제약이 막음
                reactionRepository.save(Reaction.of(postId, userId, emojiType));
                publisher.publishEvent(new ReactionAddedEvent(postId, userId));
                return new ReactionToggleResponse(true, emojiType);
            });
    }
}
