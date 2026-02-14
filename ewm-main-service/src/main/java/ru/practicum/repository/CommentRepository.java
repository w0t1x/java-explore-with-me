package ru.practicum.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.model.Comment;
import ru.practicum.model.CommentStatus;

import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findAllByAuthorId(long authorId, Pageable pageable);

    Page<Comment> findAllByEventIdAndStatus(long eventId, CommentStatus status, Pageable pageable);

    Page<Comment> findAllByStatus(CommentStatus status, Pageable pageable);

    Page<Comment> findAllByStatusAndEventId(CommentStatus status, long eventId, Pageable pageable);

    Optional<Comment> findByIdAndAuthorId(long id, long authorId);
}
