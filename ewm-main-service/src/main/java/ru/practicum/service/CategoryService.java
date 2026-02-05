package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.category.CategoryDto;
import ru.practicum.dto.category.NewCategoryDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.CategoryMapper;
import ru.practicum.model.Category;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryDto createCategory(NewCategoryDto newCategoryDto) {
        if (categoryRepository.findByName(newCategoryDto.getName()).isPresent()) {
            throw new ConflictException("Категория с таким названием уже существует: " + newCategoryDto.getName());
        }

        Category category = categoryMapper.toCategory(newCategoryDto);
        Category savedCategory = categoryRepository.save(category);
        log.info("Созданная категория с id={} and name={}", savedCategory.getId(), savedCategory.getName());

        return categoryMapper.toCategoryDto(savedCategory);
    }

    @Transactional
    public void deleteCategory(Long catId) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не был найден"));

        if (eventRepository.existsByCategoryId(catId)) {
            throw new ConflictException("Категория не является пустой");
        }

        categoryRepository.delete(category);
        log.info("Удаленная категория с id={}", catId);
    }

    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto categoryDto) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не был найден"));

        categoryRepository.findByName(categoryDto.getName())
                .ifPresent(existingCategory -> {
                    if (!existingCategory.getId().equals(catId)) {
                        throw new ConflictException("Категория с таким названием уже существует: " + categoryDto.getName());
                    }
                });

        category.setName(categoryDto.getName());
        Category updatedCategory = categoryRepository.save(category);
        log.info("Обновленная категория с id={}", catId);

        return categoryMapper.toCategoryDto(updatedCategory);
    }

    public List<CategoryDto> getCategories(Integer from, Integer size) {
        // Валидация параметров
        if (from == null || from < 0) {
            from = 0;
        }
        if (size == null || size <= 0) {
            size = 10;
        }

        int page = from / size;
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());

        return categoryRepository.findAll(pageable).stream()
                .map(categoryMapper::toCategoryDto)
                .collect(Collectors.toList());
    }

    public CategoryDto getCategory(Long catId) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не был найден"));

        return categoryMapper.toCategoryDto(category);
    }
}