package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.comment.CommentDto;
import ru.practicum.dto.comment.NewCommentDto;
import ru.practicum.dto.comment.UpdateCommentAdminRequest;
import ru.practicum.dto.comment.UpdateCommentDto;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.CommentMapper;
import ru.practicum.model.Comment;
import ru.practicum.model.CommentStatus;
import ru.practicum.model.Event;
import ru.practicum.model.EventState;
import ru.practicum.model.User;
import ru.practicum.repository.CommentRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static ru.practicum.util.PageableUtil.fromOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentDto create(long userId, long eventId, NewCommentDto dto) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Only published events can be commented");
        }

        Comment comment = Comment.builder()
                .text(dto.getText())
                .event(event)
                .author(author)
                .status(CommentStatus.PENDING)
                .createdOn(LocalDateTime.now())
                .build();

        return CommentMapper.toDto(commentRepository.save(comment));
    }

    @Transactional
    public CommentDto update(long userId, long commentId, UpdateCommentDto dto) {
        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (comment.getStatus() == CommentStatus.PUBLISHED) {
            throw new ConflictException("Published comment cannot be edited");
        }

        comment.setText(dto.getText());
        comment.setUpdatedOn(LocalDateTime.now());
        comment.setStatus(CommentStatus.PENDING);
        comment.setRejectionReason(null);
        comment.setModeratedOn(null);

        return CommentMapper.toDto(commentRepository.save(comment));
    }

    @Transactional
    public void delete(long userId, long commentId) {
        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
        commentRepository.delete(comment);
    }

    public List<CommentDto> getUserComments(long userId, int from, int size) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));

        Pageable pageable = fromOffset(from, size, Sort.by(Sort.Direction.DESC, "createdOn"));
        return commentRepository.findAllByAuthorId(userId, pageable).stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<CommentDto> getPublishedByEvent(long eventId, int from, int size) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        Pageable pageable = fromOffset(from, size, Sort.by(Sort.Direction.DESC, "createdOn"));
        return commentRepository.findAllByEventIdAndStatus(eventId, CommentStatus.PUBLISHED, pageable).stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<CommentDto> adminSearch(CommentStatus status, Long eventId, int from, int size) {
        Pageable pageable = fromOffset(from, size, Sort.by(Sort.Direction.DESC, "createdOn"));

        if (status != null && eventId != null) {
            return commentRepository.findAllByStatusAndEventId(status, eventId, pageable).stream()
                    .map(CommentMapper::toDto)
                    .collect(Collectors.toList());
        }

        if (status != null) {
            return commentRepository.findAllByStatus(status, pageable).stream()
                    .map(CommentMapper::toDto)
                    .collect(Collectors.toList());
        }

        throw new BadRequestException("status parameter is required");
    }

    @Transactional
    public CommentDto adminModerate(long commentId, UpdateCommentAdminRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));

        switch (request.getAction()) {
            case PUBLISH -> {
                comment.setStatus(CommentStatus.PUBLISHED);
                comment.setModeratedOn(LocalDateTime.now());
                comment.setRejectionReason(null);
            }
            case REJECT -> {
                if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
                    throw new BadRequestException("rejectionReason must be provided for REJECT action");
                }
                if (comment.getStatus() == CommentStatus.PUBLISHED) {
                    throw new ConflictException("Published comment cannot be rejected");
                }
                comment.setStatus(CommentStatus.REJECTED);
                comment.setModeratedOn(LocalDateTime.now());
                comment.setRejectionReason(request.getRejectionReason());
            }
            default -> throw new BadRequestException("Unknown action");
        }

        return CommentMapper.toDto(commentRepository.save(comment));
    }

    @Transactional
    public void adminDelete(long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
        commentRepository.delete(comment);
    }
}
