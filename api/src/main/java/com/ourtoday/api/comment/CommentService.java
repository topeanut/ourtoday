package com.ourtoday.api.comment;

import com.ourtoday.api.comment.dto.CommentResponse;
import com.ourtoday.api.common.error.BusinessException;
import com.ourtoday.api.common.error.ErrorCode;
import com.ourtoday.api.post.PostService;
import com.ourtoday.api.user.User;
import com.ourtoday.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostService postService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID userId, UUID postId) {
        postService.getPostInMyCouple(userId, postId);
        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        Map<UUID, User> users = loadUsers(comments);
        return comments.stream().map(c -> toResponse(c, users)).toList();
    }

    @Transactional
    public CommentResponse create(UUID userId, UUID postId, String content) {
        postService.getPostInMyCouple(userId, postId);
        Comment comment = commentRepository.save(Comment.create(postId, userId, content));
        return toResponse(comment, loadUsers(List.of(comment)));
    }

    @Transactional
    public CommentResponse update(UUID userId, UUID postId, UUID commentId, String content) {
        Comment comment = findOwned(userId, postId, commentId);
        comment.update(content);
        commentRepository.flush();
        return toResponse(comment, loadUsers(List.of(comment)));
    }

    @Transactional
    public void delete(UUID userId, UUID postId, UUID commentId) {
        commentRepository.delete(findOwned(userId, postId, commentId));
    }

    private Comment findOwned(UUID userId, UUID postId, UUID commentId) {
        postService.getPostInMyCouple(userId, postId);
        Comment comment = commentRepository.findById(commentId)
            .filter(c -> c.getPostId().equals(postId))
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다."));
        if (!comment.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "작성자만 수정/삭제할 수 있습니다.");
        }
        return comment;
    }

    private Map<UUID, User> loadUsers(List<Comment> comments) {
        List<UUID> ids = comments.stream().map(Comment::getAuthorId).distinct().toList();
        return userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private CommentResponse toResponse(Comment comment, Map<UUID, User> users) {
        return CommentResponse.of(comment, users.get(comment.getAuthorId()));
    }
}
