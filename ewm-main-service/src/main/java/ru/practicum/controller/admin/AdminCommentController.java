package ru.practicum.controller.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.comment.CommentDto;
import ru.practicum.dto.comment.UpdateCommentAdminRequest;
import ru.practicum.model.CommentStatus;
import ru.practicum.service.CommentService;

import java.util.List;

@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@Validated
public class AdminCommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentDto> search(@RequestParam(defaultValue = "PENDING") CommentStatus status,
                                   @RequestParam(required = false) Long eventId,
                                   @RequestParam(defaultValue = "0") @PositiveOrZero int from,
                                   @RequestParam(defaultValue = "10") @Positive int size) {
        return commentService.adminSearch(status, eventId, from, size);
    }

    @PatchMapping("/{commentId}")
    public CommentDto moderate(@PathVariable long commentId,
                               @RequestBody @Valid UpdateCommentAdminRequest request) {
        return commentService.adminModerate(commentId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long commentId) {
        commentService.adminDelete(commentId);
    }
}
