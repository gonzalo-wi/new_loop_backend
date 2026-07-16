package com.loop.new_loop_api.branches.service.iService;

import com.loop.new_loop_api.branches.dto.BranchResponse;
import com.loop.new_loop_api.branches.dto.CreateBranchRequest;
import com.loop.new_loop_api.branches.dto.UpdateBranchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BranchService {

    BranchResponse createBranch(CreateBranchRequest request);
    Page<BranchResponse> getAllBranches(Pageable pageable);
    BranchResponse getBranchById(UUID id);
    BranchResponse updateBranch(UUID id, UpdateBranchRequest request);
    void deactivateBranch(UUID id);
    void activateBranch(UUID id);
}
