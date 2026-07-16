package com.loop.new_loop_api.branches.controller;

import com.loop.new_loop_api.branches.dto.BranchResponse;
import com.loop.new_loop_api.branches.dto.CreateBranchRequest;
import com.loop.new_loop_api.branches.dto.UpdateBranchRequest;
import com.loop.new_loop_api.branches.service.iService.BranchService;
import com.loop.new_loop_api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(@Valid @RequestBody CreateBranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(branchService.createBranch(request)));
    }


    @Parameters({
            @Parameter(name = "page",  description = "Page number (0-indexed)", example = "0"),
            @Parameter(name = "size",  description = "Page size",               example = "20"),
            @Parameter(name = "sort",  description = "Sort: field,direction",   example = "name,asc")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BranchResponse>>> getAllBranches(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getAllBranches(pageable)));
    }



    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranchById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getBranchById(id)));
    }


    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBranchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.updateBranch(id, request)));
    }


    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateBranch(@PathVariable UUID id) {
        branchService.deactivateBranch(id);
        return ResponseEntity.noContent().build();
    }

    
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateBranch(@PathVariable UUID id) {
        branchService.activateBranch(id);
        return ResponseEntity.noContent().build();
    }
}
