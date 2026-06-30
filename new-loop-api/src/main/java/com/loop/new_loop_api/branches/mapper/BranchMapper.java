package com.loop.new_loop_api.branches.mapper;

import com.loop.new_loop_api.branches.dto.BranchResponse;
import com.loop.new_loop_api.branches.dto.CreateBranchRequest;
import com.loop.new_loop_api.branches.dto.UpdateBranchRequest;
import com.loop.new_loop_api.branches.entity.Branch;
import org.springframework.stereotype.Component;

@Component
public class BranchMapper {

    public Branch toEntity(CreateBranchRequest request) {
        return Branch.builder()
                .name(request.getName())
                .code(request.getCode())
                .address(request.getAddress())
                .locality(request.getLocality())
                .province(request.getProvince())
                .cuit(request.getCuit())
                .vatCondition(request.getVatCondition())
                .active(true)
                .build();
    }

    public BranchResponse toResponse(Branch branch) {
        return BranchResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .code(branch.getCode())
                .address(branch.getAddress())
                .locality(branch.getLocality())
                .province(branch.getProvince())
                .cuit(branch.getCuit())
                .vatCondition(branch.getVatCondition())
                .active(branch.getActive())
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }

    public void updateEntity(UpdateBranchRequest request, Branch branch) {
        if (request.getCode() != null)         branch.setCode(request.getCode());
        if (request.getName() != null)         branch.setName(request.getName());
        if (request.getAddress() != null)      branch.setAddress(request.getAddress());
        if (request.getLocality() != null)     branch.setLocality(request.getLocality());
        if (request.getProvince() != null)     branch.setProvince(request.getProvince());
        if (request.getCuit() != null)         branch.setCuit(request.getCuit());
        if (request.getVatCondition() != null) branch.setVatCondition(request.getVatCondition());
    }
}
