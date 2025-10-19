package com.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.common.exception.ServerException;
import com.crm.common.result.PageResult;
import com.crm.entity.Department;
import com.crm.entity.SysManager;
import com.crm.mapper.DepartmentMapper;
import com.crm.mapper.SysManagerMapper;
import com.crm.query.DepartmentQuery;
import com.crm.query.IdQuery;
import com.crm.service.DepartmentService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author zhz
 * @since 2025-10-12
 */
@Service
@AllArgsConstructor
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {


    @Override
    public PageResult<Department> getPage(DepartmentQuery query) {
        // 1、构造条件查询 wrapper
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        if (query.getName() != null && !query.getName().isEmpty()) {
            wrapper.like(Department::getName, query.getName());
        }

        // 先查出所有符合条件的部门，用于后续树结构构建
        List<Department> allMatched = baseMapper.selectList(wrapper);
        if (allMatched.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L);
        }

        // 2、找到最小层级（顶级层级）
        Integer minLevel = allMatched.stream()
                .map(Department::getLevel)
                .min(Integer::compareTo)
                .orElse(0);

        // 3、筛选出顶级部门
        List<Department> topDepartments = allMatched.stream()
                .filter(d -> d.getLevel().equals(minLevel))
                .toList();

        // 4、分页顶级部门
        int total = topDepartments.size();
        int fromIndex = (query.getPage() - 1) * query.getLimit();
        int toIndex = Math.min(fromIndex + query.getLimit(), total);

        if (fromIndex >= total) {
            return new PageResult<>(Collections.emptyList(), total);
        }

        List<Department> pagedTopDepartments = topDepartments.subList(fromIndex, toIndex);

        // 5、为每个顶级部门构建完整的子树
        pagedTopDepartments.forEach(dept -> getChildList(dept, allMatched));

        return new PageResult<>(pagedTopDepartments, total);
    }

    private void getChildList(Department dept, List<Department> allMatched) {
        List<Department> childList = allMatched.stream()
                .filter(d -> d.getParentId().equals(dept.getId()))
                .toList();
        dept.setChildren(childList);
    }

    @Override
    public List<Department> getList() {
//        1、查询父级部门列表,如果列表为空，返回空集合
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>();
        wrapper.eq(Department::getParentId, 0);
        List<Department> parentDepartments = baseMapper.selectList(wrapper);
        if (parentDepartments.isEmpty()) {
            return new ArrayList<>();
        }
        wrapper.clear();
//        2、查询子部门列表，用于父子关系的递归
        wrapper.ne(Department::getParentId, 0);
        List<Department> childDepartments = baseMapper.selectList(wrapper);
        if (childDepartments.isEmpty()) {
            return parentDepartments;
        }
//        3、递归构建父子关系
        parentDepartments.forEach(item -> {
            getChildList(item, childDepartments);
        });
        return parentDepartments;
    }

    @Override
    public void saveOrEditDepartment(Department department) {
//        1、查询新增/修改的部门名称是不是已经存在了，如果存在直接抛出异常
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>().eq(Department::getName, department.getName());
        if (department.getId() == null) {
            wrapper.eq(Department::getParentId, department.getParentId());
            List<Department> departments = baseMapper.selectList(wrapper);
            if (!departments.isEmpty()) {
                throw new ServerException("部门名称已存在");
            }
//            2、新增是判断是否有上级，如果有上级要获取上级部门的部门信息，存储父子关系
            if (department.getParentId() != null) {
                Department parentDepart = baseMapper.selectById(department.getParentId());
                if (parentDepart == null) {
                    throw new ServerException("上级部门不存在");
                } else {
                    if (parentDepart.getParentIds() == null || parentDepart.getParentIds().isEmpty()) {
                        department.setParentIds(parentDepart.getId().toString());
                    } else {
                        department.setParentIds(parentDepart.getParentIds() + "," + parentDepart.getId());
                    }
                    department.setLevel(parentDepart.getLevel() + 1);
                }
            }
            department.setDeleteFlag((byte) 0);

            baseMapper.insert(department);
        } else {
            Department aDepartment = baseMapper.selectById(department.getId());
            if (aDepartment == null) {
                throw new ServerException("部门不存在");
            }
            wrapper.ne(Department::getId, department.getId()).eq(Department::getParentId, department.getParentId());
            List<Department> departments = baseMapper.selectList(wrapper);
            if (!departments.isEmpty()) {
                throw new ServerException("部门名称已存在");
            }
//            3、修改部门存在上级部门且上级部门的信息发生了变化时，要修改关联的父子关系关联的id
            if (department.getParentId() != 0 && !Objects.equals(department.getParentId(), aDepartment.getParentId())) {
                Department parentDepart = baseMapper.selectById(department.getParentId());
                String parentIds = aDepartment.getParentIds();
                String newParentIds = "";
                if (parentDepart == null) {
                    throw new ServerException("上级部门不存在");
                }
                if (parentDepart.getParentIds() == null || parentDepart.getParentIds().isEmpty()) {
                    newParentIds = parentDepart.getId().toString();

                } else {
                    newParentIds = parentDepart.getParentIds() + "," + parentDepart.getId();
                }
                department.setLevel(parentDepart.getLevel() + 1);
                department.setParentIds(newParentIds);
                String finalNewParentIds = newParentIds;
                baseMapper.selectList(new LambdaQueryWrapper<Department>().eq(Department::getParentIds, parentIds)).forEach(item -> {
                    item.setParentIds(item.getParentIds().replace(parentIds, finalNewParentIds));
                    baseMapper.updateById(item);
                });
            }
            baseMapper.updateById(department);
        }

    }

    private final SysManagerMapper sysManagerMapper;
    @Override
    public void removeDepartment(IdQuery query) {
        List<SysManager> sysManagers = sysManagerMapper.selectList(new LambdaQueryWrapper<SysManager>().eq(SysManager::getId, query.getId()));
        if (!sysManagers.isEmpty()) {
            throw new ServerException("部门下有管理员,请解绑后再删除");
        }
        // 删除该部门以及子部门
        List<Department> departments = baseMapper.selectList(new LambdaQueryWrapper<Department>().like(Department::getParentIds, query.getId()).or().eq(Department::getId, query.getId()));
        removeBatchByIds(departments);
    }

}
