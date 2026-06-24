package com.loop.new_loop_api.branches.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.branches.dto.BranchResponse;
import com.loop.new_loop_api.branches.dto.CreateBranchRequest;
import com.loop.new_loop_api.branches.dto.UpdateBranchRequest;
import com.loop.new_loop_api.branches.exception.BranchCodeAlreadyExistsException;
import com.loop.new_loop_api.branches.exception.BranchNotFoundException;
import com.loop.new_loop_api.branches.mapper.BranchMapper;
import com.loop.new_loop_api.branches.repository.BranchRepository;
import com.loop.new_loop_api.branches.entity.Branch;
import com.loop.new_loop_api.branches.service.iService.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final BranchMapper     branchMapper;
    private final AuditService     auditService;

    @Override
    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        if (branchRepository.existsByCode(request.getCode())) {
            throw new BranchCodeAlreadyExistsException(request.getCode());
        }
        var branch   = branchMapper.toEntity(request);
        var response = branchMapper.toResponse(branchRepository.save(branch));
        auditService.register("CREATE_BRANCH", "Branch", response.getId(), null, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BranchResponse> getAllBranches(Pageable pageable) {
        return branchRepository.findAll(pageable)
                .map(branchMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getBranchById(UUID id) {
        return branchMapper.toResponse(findBranchById(id));
    }

    @Override
    @Transactional
    public BranchResponse updateBranch(UUID id, UpdateBranchRequest request) {
        var branch   = findBranchById(id);
        var oldValue = branchMapper.toResponse(branch);
        branchMapper.updateEntity(request, branch);
        var response = branchMapper.toResponse(branchRepository.save(branch));
        auditService.register("UPDATE_BRANCH", "Branch", id, oldValue, response);
        return response;
    }

    @Override
    @Transactional
    public void deactivateBranch(UUID id) {
        var branch = findBranchById(id);
        branch.setActive(false);
        branchRepository.save(branch);
        auditService.register("DEACTIVATE_BRANCH", "Branch", id, null, null);
    }

    @Override
    @Transactional
    public void activateBranch(UUID id) {
        var branch = findBranchById(id);
        branch.setActive(true);
        branchRepository.save(branch);
        auditService.register("ACTIVATE_BRANCH", "Branch", id, null, null);
    }

    private Branch findBranchById(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException(id));
    }
}
