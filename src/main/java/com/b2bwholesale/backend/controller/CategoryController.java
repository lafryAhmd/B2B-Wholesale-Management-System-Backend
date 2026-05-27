package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Category;
import com.b2bwholesale.backend.repositories.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    // GET all categories
    @GetMapping
    public List<Category> getAll() {
        return categoryRepository.findByIsActiveTrue();
    }

    // POST create category
    @PostMapping
    public Category create(@RequestBody Category category) {
        return categoryRepository.save(category);
    }

    // GET by id
    @GetMapping("/{id}")
    public Category getById(@PathVariable Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Category not found"));
    }

    // PUT update
    @PutMapping("/{id}")
    public Category update(@PathVariable Long id,
                           @RequestBody Category updated) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Category not found"));
        cat.setName(updated.getName());
        cat.setDescription(updated.getDescription());
        return categoryRepository.save(cat);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        categoryRepository.deleteById(id);
        return ResponseEntity.ok("Deleted");
    }
}














