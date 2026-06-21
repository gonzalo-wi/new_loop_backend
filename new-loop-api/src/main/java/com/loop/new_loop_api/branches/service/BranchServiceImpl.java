package com.loop.new_loop_api.branches.service;

import com.loop.new_loop_api.branches.dto.BranchResponse;
import com.loop.new_loop_api.branches.dto.CreateBranchRequest;
import com.loop.new_loop_api.branches.dto.UpdateBranchRequest;
import com.loop.new_loop_api.branches.exception.BranchCodeAlreadyExistsException;
import com.loop.new_loop_api.branches.exception.BranchNotFoundException;
import com.loop.new_loop_api.branches.mapper.BranchMapper;
import com.loop.new_loop_api.branches.repository.BranchRepository;
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
    private final BranchMapper branchMapper;

    @Override
    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        if (branchRepository.existsByCode(request.getCode())) {
            throw new BranchCodeAlreadyExistsException(request.getCode());
        }
        var branch = branchMapper.toEntity(request);
        return branchMapper.toResponse(branchRepository.save(branch));
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
        return branchRepository.findById(id)
                .map(branchMapper::toResponse)
                .orElseThrow(() -> new BranchNotFoundException(id));
    }

    @Override
    @Transactional
    public BranchResponse updateBranch(UUID id, UpdateBranchRequest request) {
        var branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException(id));
        branchMapper.updateEntity(request, branch);
        return branchMapper.toResponse(branchRepository.save(branch));
    }

    @Override
    @Transactional
    public void deactivateBranch(UUID id) {
        var branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException(id));
        branch.setActive(false);
        branchRepository.save(branch);
    }

    @Override
    @Transactional
    public void activateBranch(UUID id) {
        var branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException(id));
        branch.setActive(true);
        branchRepository.save(branch);
    }
}
