package com.queue.controller;

import com.queue.common.Result;
import com.queue.entity.BusinessTypeGroup;
import com.queue.service.BusinessTypeGroupService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/business-type-groups")
public class BusinessTypeGroupController {

    private final BusinessTypeGroupService businessTypeGroupService;

    public BusinessTypeGroupController(BusinessTypeGroupService businessTypeGroupService) {
        this.businessTypeGroupService = businessTypeGroupService;
    }

    @GetMapping
    public Result<List<BusinessTypeGroup>> list() {
        return Result.ok(businessTypeGroupService.listAll());
    }

    @PostMapping
    public Result<BusinessTypeGroup> create(@RequestBody BusinessTypeGroup group) {
        return Result.ok(businessTypeGroupService.create(group));
    }

    @PutMapping("/{id}")
    public Result<BusinessTypeGroup> update(@PathVariable Long id, @RequestBody BusinessTypeGroup group) {
        group.setId(id);
        return Result.ok(businessTypeGroupService.update(group));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        businessTypeGroupService.delete(id);
        return Result.ok();
    }
}
