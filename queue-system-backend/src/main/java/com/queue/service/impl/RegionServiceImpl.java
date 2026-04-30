package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.RegionImportResult;
import com.queue.dto.RegionPageRequest;
import com.queue.dto.RegionSortRequest;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.mapper.RegionMapper;
import com.queue.service.AuthContextService;
import com.queue.service.RegionService;
import com.queue.service.QrCodeRecordService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class RegionServiceImpl implements RegionService {
    private final RegionMapper regionMapper;
    private final QrCodeRecordService qrCodeRecordService;
    private final AuthContextService authContextService;

    private static final String[] IMPORT_HEADERS = {"区域名称", "区划代码", "级别", "父级区划代码", "排序", "公告内容"};

    public RegionServiceImpl(RegionMapper regionMapper, @Lazy QrCodeRecordService qrCodeRecordService, @Lazy AuthContextService authContextService) {
        this.regionMapper = regionMapper;
        this.qrCodeRecordService = qrCodeRecordService;
        this.authContextService = authContextService;
    }

    @Override
    public List<Region> listByLevel(String level) {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .eq(Region::getLevel, level)
                .orderByAsc(Region::getSortOrder));
    }

    @Override
    public List<Region> listByParentId(Long parentId) {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .eq(Region::getParentId, parentId)
                .orderByAsc(Region::getSortOrder));
    }

    @Override
    public Region getById(Long id) {
        return regionMapper.selectById(id);
    }

    @Override
    public Region getByCode(String code) {
        return regionMapper.selectOne(new LambdaQueryWrapper<Region>()
                .eq(Region::getRegionCode, code));
    }

    @Override
    public String getFullRegionName(String code) {
        Region region = getByCode(code);
        if (region == null) return "";
        if (region.getParentId() != null) {
            Region parent = getById(region.getParentId());
            if (parent != null) {
                return parent.getRegionName() + "-" + region.getRegionName();
            }
        }
        return region.getRegionName();
    }

    @Override
    public Region create(Region region) {
        checkRegionCodeDuplicate(region.getRegionCode(), null);
        regionMapper.insert(region);
        return region;
    }

    @Override
    public Region update(Region region) {
        Region existing = regionMapper.selectById(region.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        checkRegionCodeDuplicate(region.getRegionCode(), region.getId());
        regionMapper.updateById(region);
        return region;
    }

    /**
     * 校验区划代码唯一性（排除指定id）
     */
    private void checkRegionCodeDuplicate(String regionCode, Long excludeId) {
        if (regionCode == null || regionCode.trim().isEmpty()) {
            return;
        }
        Region duplicate = getByCode(regionCode.trim());
        if (duplicate != null && !duplicate.getId().equals(excludeId)) {
            String fullName = buildFullPath(duplicate);
            throw new BusinessException(40001, fullName + "|" + duplicate.getRegionCode());
        }
    }

    /**
     * 构建区域的完整路径名称（如：深圳市-宝安区-新安街道）
     */
    private String buildFullPath(Region region) {
        StringBuilder sb = new StringBuilder(region.getRegionName());
        Region current = region;
        while (current.getParentId() != null) {
            Region parent = getById(current.getParentId());
            if (parent == null) break;
            sb.insert(0, parent.getRegionName() + "-");
            current = parent;
        }
        return sb.toString();
    }

    @Override
    public void delete(Long id) {
        // 检查是否有子区域
        List<Region> children = listByParentId(id);
        if (!children.isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "当前区域下存在下级区域，请先删除下级区域");
        }
        // 级联删除该区域的二维码记录
        qrCodeRecordService.deleteByRegionIds(List.of(id));
        // 物理删除区域
        regionMapper.physicalDeleteById(id);
    }

    @Override
    public List<Region> listAll() {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .orderByAsc(Region::getLevel)
                .orderByAsc(Region::getSortOrder));
    }

    @Override
    @Transactional
    public void batchUpdateSort(List<RegionSortRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        // 校验：所有区域的 parentId 必须一致（同层级排序）
        Long expectedParentId = null;
        for (RegionSortRequest req : requests) {
            Region region = regionMapper.selectById(req.getId());
            if (region == null) {
                throw new BusinessException(50001, "区域不存在: id=" + req.getId());
            }
            if (expectedParentId == null) {
                expectedParentId = region.getParentId();
            } else if (!expectedParentId.equals(region.getParentId())) {
                throw new BusinessException(50001, "不允许跨层级排序，区域「" + region.getRegionName() + "」与同组其他区域不在同一层级");
            }
        }

        for (RegionSortRequest req : requests) {
            Region region = new Region();
            region.setId(req.getId());
            region.setSortOrder(req.getSortOrder());
            regionMapper.updateById(region);
        }
    }

    @Override
    public IPage<Region> listPage(RegionPageRequest request) {
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        Page<Region> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Region> wrapper = new LambdaQueryWrapper<>();

        // 按城市筛选：包含市级区域本身 + 该市下所有子/孙级区域
        if (request.getCityId() != null) {
            // 先查该市下所有镇区ID
            List<Region> towns = regionMapper.selectList(
                    new LambdaQueryWrapper<Region>().eq(Region::getParentId, request.getCityId()));
            List<Long> townIds = towns.stream().map(Region::getId).collect(Collectors.toList());

            // 查询条件：id = cityId OR parentId = cityId OR parentId IN (townIds)
            wrapper.and(w -> {
                w.eq(Region::getId, request.getCityId());
                w.or().eq(Region::getParentId, request.getCityId());
                if (!townIds.isEmpty()) {
                    w.or().in(Region::getParentId, townIds);
                }
            });
        }

        // 按级别筛选
        if (StringUtils.hasText(request.getLevel())) {
            wrapper.eq(Region::getLevel, request.getLevel());
        }

        // 关键词搜索
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(Region::getRegionName, kw).or().like(Region::getRegionCode, kw));
        }

        wrapper.orderByAsc(Region::getLevel).orderByAsc(Region::getSortOrder);

        IPage<Region> result = regionMapper.selectPage(page, wrapper);

        // 补充 parentName
        if (!result.getRecords().isEmpty()) {
            List<Long> parentIds = result.getRecords().stream()
                    .map(Region::getParentId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());

            Map<Long, String> parentNameMap = Collections.emptyMap();
            if (!parentIds.isEmpty()) {
                List<Region> parents = regionMapper.selectBatchIds(parentIds);
                parentNameMap = parents.stream()
                        .collect(Collectors.toMap(Region::getId, Region::getRegionName));
            }

            final Map<Long, String> nameMap = parentNameMap;
            for (Region region : result.getRecords()) {
                if (region.getParentId() != null) {
                    String parentName = nameMap.get(region.getParentId());
                    if (parentName != null) {
                        region.setParentName(parentName);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<Long> getDescendantRegionIds(Long regionId) {
        if (regionId == null) {
            return Collections.emptyList();
        }
        return regionMapper.selectDescendantIds(regionId);
    }

    @Override
    public byte[] generateImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet templateSheet = workbook.createSheet("区域导入模板");
            Row headerRow = templateSheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(IMPORT_HEADERS[i]);
                templateSheet.setColumnWidth(i, switch (i) {
                    case 0 -> 18 * 256;
                    case 1 -> 18 * 256;
                    case 2 -> 12 * 256;
                    case 3 -> 20 * 256;
                    case 4 -> 10 * 256;
                    default -> 36 * 256;
                });
            }

            Sheet exampleSheet = workbook.createSheet("示例数据");
            Row exampleHeaderRow = exampleSheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                exampleHeaderRow.createCell(i).setCellValue(IMPORT_HEADERS[i]);
                exampleSheet.setColumnWidth(i, templateSheet.getColumnWidth(i));
            }
            String[][] exampleRows = {
                    {"深圳市", "440300", "city", "", "0", ""},
                    {"南山区", "440305", "镇/区级", "440300", "10", "南山区大厅工作日 9:00-18:00"},
                    {"粤海街道", "440305001", "街道级", "440305", "20", ""}
            };
            for (int i = 0; i < exampleRows.length; i++) {
                Row row = exampleSheet.createRow(i + 1);
                for (int j = 0; j < exampleRows[i].length; j++) {
                    row.createCell(j).setCellValue(exampleRows[i][j]);
                }
            }

            Sheet instructionSheet = workbook.createSheet("填写说明");
            String[][] instructions = {
                    {"字段", "说明"},
                    {"区域名称", "必填，长度建议不超过 50 字。"},
                    {"区划代码", "必填，必须唯一。"},
                    {"级别", "必填，支持 city/town/street 或 市级/镇区级/街道级。"},
                    {"父级区划代码", "市级区域留空；镇区级填写所属市级代码；街道级填写所属镇区级代码。"},
                    {"排序", "选填，整数，留空时默认按 0 导入。"},
                    {"公告内容", "选填，最长 500 字。"},
                    {"导入规则", "支持同一文件内多级区域一起导入，系统会自动按父子关系处理。"},
                    {"示例页", "可参考“示例数据”工作表中的市级、镇/区级、街道级三层示例。"},
                    {"示例", "深圳市 | 440300 | city |  | 0 | "},
                    {"示例", "南山区 | 440305 | town | 440300 | 10 | 南山区大厅工作日 9:00-18:00"},
                    {"示例", "粤海街道 | 440305001 | street | 440305 | 20 | "}
            };
            for (int i = 0; i < instructions.length; i++) {
                Row row = instructionSheet.createRow(i);
                row.createCell(0).setCellValue(instructions[i][0]);
                row.createCell(1).setCellValue(instructions[i][1]);
            }
            instructionSheet.setColumnWidth(0, 18 * 256);
            instructionSheet.setColumnWidth(1, 80 * 256);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "生成导入模板失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegionImportResult importRegions(MultipartFile file, SysUser currentUser) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请先选择要导入的 Excel 文件");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(400, "导入文件缺少数据工作表");
            }

            Sheet sheet = workbook.getSheet("区域导入模板");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            DataFormatter formatter = new DataFormatter();
            validateImportHeader(sheet.getRow(0), formatter);
            List<RegionImportRow> importRows = parseImportRows(sheet, formatter);
            if (importRows.isEmpty()) {
                throw new BusinessException(400, "导入文件中没有可导入的数据");
            }

            Map<String, Region> regionByCode = listAll().stream()
                    .filter(region -> StringUtils.hasText(region.getRegionCode()))
                    .collect(Collectors.toMap(
                            region -> region.getRegionCode().trim(),
                            region -> region,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            List<RegionImportRow> pendingRows = new ArrayList<>(importRows);
            int importedCount = 0;

            while (!pendingRows.isEmpty()) {
                boolean progressed = false;
                Iterator<RegionImportRow> iterator = pendingRows.iterator();
                while (iterator.hasNext()) {
                    RegionImportRow importRow = iterator.next();
                    Region parent = resolveParent(importRow, regionByCode);
                    if (requiresParent(importRow.level()) && parent == null) {
                        continue;
                    }

                    validateImportRow(importRow, parent, currentUser, regionByCode);

                    Region region = new Region();
                    region.setRegionName(importRow.name());
                    region.setRegionCode(importRow.code());
                    region.setLevel(importRow.level());
                    region.setParentId(parent == null ? null : parent.getId());
                    region.setSortOrder(importRow.sortOrder());
                    region.setAnnouncementText(importRow.announcementText());

                    Region created = create(region);
                    regionByCode.put(created.getRegionCode().trim(), created);
                    iterator.remove();
                    importedCount++;
                    progressed = true;
                }

                if (!progressed) {
                    throw new BusinessException(400, buildUnresolvedImportMessage(pendingRows, regionByCode, currentUser));
                }
            }

            return new RegionImportResult(importedCount);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "读取 Excel 失败，请使用系统模板并上传 .xlsx 或 .xls 文件");
        }
    }

    private void validateImportHeader(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new BusinessException(400, "导入文件缺少表头，请先下载模板");
        }
        for (int i = 0; i < IMPORT_HEADERS.length; i++) {
            String actual = formatter.formatCellValue(headerRow.getCell(i)).trim();
            if (!IMPORT_HEADERS[i].equals(actual)) {
                throw new BusinessException(400, "模板表头不正确，请先下载最新模板");
            }
        }
    }

    private List<RegionImportRow> parseImportRows(Sheet sheet, DataFormatter formatter) {
        List<RegionImportRow> rows = new ArrayList<>();
        Set<String> batchCodes = new HashSet<>();

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String name = formatter.formatCellValue(row.getCell(0)).trim();
            String code = formatter.formatCellValue(row.getCell(1)).trim();
            String levelText = formatter.formatCellValue(row.getCell(2)).trim();
            String parentCode = formatter.formatCellValue(row.getCell(3)).trim();
            String sortOrderText = formatter.formatCellValue(row.getCell(4)).trim();
            String announcementText = formatter.formatCellValue(row.getCell(5)).trim();

            if (!StringUtils.hasText(name)
                    && !StringUtils.hasText(code)
                    && !StringUtils.hasText(levelText)
                    && !StringUtils.hasText(parentCode)
                    && !StringUtils.hasText(sortOrderText)
                    && !StringUtils.hasText(announcementText)) {
                continue;
            }

            int displayRowNum = rowIndex + 1;
            if (!StringUtils.hasText(name)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行缺少区域名称");
            }
            if (!StringUtils.hasText(code)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行缺少区划代码");
            }
            if (name.length() > 50) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行区域名称长度不能超过 50");
            }
            if (code.length() > 20) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行区划代码长度不能超过 20");
            }
            if (!batchCodes.add(code)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行区划代码重复: " + code);
            }
            if (announcementText.length() > 500) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行公告内容长度不能超过 500");
            }

            String normalizedLevel = normalizeLevel(levelText, displayRowNum);
            if ("city".equals(normalizedLevel) && StringUtils.hasText(parentCode)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行市级区域不能填写父级区划代码");
            }

            rows.add(new RegionImportRow(
                    displayRowNum,
                    name,
                    code,
                    normalizedLevel,
                    StringUtils.hasText(parentCode) ? parentCode : null,
                    parseSortOrder(sortOrderText, displayRowNum),
                    StringUtils.hasText(announcementText) ? announcementText : null
            ));
        }

        return rows;
    }

    private Integer parseSortOrder(String sortOrderText, int displayRowNum) {
        if (!StringUtils.hasText(sortOrderText)) {
            return 0;
        }
        try {
            return Integer.parseInt(sortOrderText);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "第 " + displayRowNum + " 行排序必须为整数");
        }
    }

    private String normalizeLevel(String levelText, int displayRowNum) {
        if (!StringUtils.hasText(levelText)) {
            throw new BusinessException(400, "第 " + displayRowNum + " 行缺少级别");
        }
        String normalized = levelText.trim().toLowerCase();
        return switch (normalized) {
            case "city", "市", "市级", "市级区域" -> "city";
            case "town", "镇", "区", "镇区级", "镇/区级", "区级" -> "town";
            case "street", "街道", "街道级" -> "street";
            default -> throw new BusinessException(400, "第 " + displayRowNum + " 行级别无效，请填写 city/town/street 或中文级别");
        };
    }

    private Region resolveParent(RegionImportRow importRow, Map<String, Region> regionByCode) {
        if (!requiresParent(importRow.level())) {
            return null;
        }
        if (!StringUtils.hasText(importRow.parentCode())) {
            return null;
        }
        return regionByCode.get(importRow.parentCode());
    }

    private void validateImportRow(RegionImportRow importRow, Region parent, SysUser currentUser, Map<String, Region> regionByCode) {
        if (regionByCode.containsKey(importRow.code())) {
            throw new BusinessException(400, "第 " + importRow.rowNum() + " 行区划代码已存在: " + importRow.code());
        }

        if ("city".equals(importRow.level())) {
            if (!authContextService.isSuperAdmin(currentUser)) {
                throw new BusinessException(403, "第 " + importRow.rowNum() + " 行仅超级管理员可以导入市级区域");
            }
            return;
        }

        if (!StringUtils.hasText(importRow.parentCode())) {
            throw new BusinessException(400, "第 " + importRow.rowNum() + " 行缺少父级区划代码");
        }
        if (parent == null) {
            throw new BusinessException(400, "第 " + importRow.rowNum() + " 行父级区划代码不存在: " + importRow.parentCode());
        }
        if (!isValidParentLevel(parent.getLevel(), importRow.level())) {
            throw new BusinessException(400, "第 " + importRow.rowNum() + " 行父级层级不匹配，当前级别为 " + importRow.level());
        }
        authContextService.assertRegionAccess(currentUser, parent.getId());
    }

    private boolean requiresParent(String level) {
        return !"city".equals(level);
    }

    private boolean isValidParentLevel(String parentLevel, String currentLevel) {
        return ("city".equals(parentLevel) && "town".equals(currentLevel))
                || ("town".equals(parentLevel) && "street".equals(currentLevel));
    }

    private String buildUnresolvedImportMessage(List<RegionImportRow> pendingRows, Map<String, Region> regionByCode, SysUser currentUser) {
        List<String> messages = new ArrayList<>();
        int limit = Math.min(pendingRows.size(), 5);
        for (int i = 0; i < limit; i++) {
            RegionImportRow row = pendingRows.get(i);
            if (!requiresParent(row.level())) {
                messages.add("第 " + row.rowNum() + " 行无法导入，请检查权限或级别配置");
                continue;
            }
            if (!StringUtils.hasText(row.parentCode())) {
                messages.add("第 " + row.rowNum() + " 行缺少父级区划代码");
                continue;
            }
            Region parent = regionByCode.get(row.parentCode());
            if (parent == null) {
                messages.add("第 " + row.rowNum() + " 行父级区划代码不存在或未成功导入: " + row.parentCode());
                continue;
            }
            if (!isValidParentLevel(parent.getLevel(), row.level())) {
                messages.add("第 " + row.rowNum() + " 行父级层级不匹配");
                continue;
            }
            if (!authContextService.isSuperAdmin(currentUser)) {
                try {
                    authContextService.assertRegionAccess(currentUser, parent.getId());
                } catch (BusinessException e) {
                    messages.add("第 " + row.rowNum() + " 行无权限在父级区域下导入");
                }
            }
        }
        if (pendingRows.size() > limit) {
            messages.add("其余 " + (pendingRows.size() - limit) + " 行也存在未解决的父级或权限问题");
        }
        return String.join("；", messages);
    }

    private record RegionImportRow(
            int rowNum,
            String name,
            String code,
            String level,
            String parentCode,
            Integer sortOrder,
            String announcementText
    ) {
    }
}
