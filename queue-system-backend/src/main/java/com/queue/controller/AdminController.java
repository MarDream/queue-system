package com.queue.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.dto.AdminTicketVO;
import com.queue.dto.BusinessTypeDetailVO;
import com.queue.dto.CounterImportResult;
import com.queue.dto.CounterRecentServiceRow;
import com.queue.dto.CounterDTO;
import com.queue.dto.CounterStatsVO;
import com.queue.dto.CounterStatsSummaryRow;
import com.queue.entity.BusinessType;
import com.queue.entity.Counter;
import com.queue.entity.CounterBusiness;
import com.queue.entity.CounterOperator;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.entity.Ticket;
import com.queue.enums.CounterStatus;
import com.queue.enums.TicketStatus;
import com.queue.mapper.AnalyticsMapper;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterBusinessMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.CounterOperatorMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.AuthContextService;
import com.queue.service.BusinessTypeService;
import com.queue.service.PhoneCryptoService;
import com.queue.service.QueueService;
import com.queue.service.RegionBusinessService;
import com.queue.service.RegionService;
import com.queue.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private static final String[] COUNTER_IMPORT_HEADERS = {"所属区域区划代码", "窗口编号", "窗口名称", "支持业务类型"};

    private final BusinessTypeService businessTypeService;
    private final RegionBusinessService regionBusinessService;
    private final TicketService ticketService;
    private final RegionService regionService;
    private final BusinessTypeMapper businessTypeMapper;
    private final AnalyticsMapper analyticsMapper;
    private final CounterMapper counterMapper;
    private final CounterBusinessMapper counterBusinessMapper;
    private final CounterOperatorMapper counterOperatorMapper;
    private final TicketMapper ticketMapper;
    private final SysUserMapper sysUserMapper;
    private final QueueService queueService;
    private final AuthContextService authContextService;
    private final PhoneCryptoService phoneCryptoService;

    public AdminController(BusinessTypeService businessTypeService,
                          RegionBusinessService regionBusinessService,
                          TicketService ticketService,
                          RegionService regionService,
                          BusinessTypeMapper businessTypeMapper,
                          AnalyticsMapper analyticsMapper,
                          CounterMapper counterMapper,
                          CounterBusinessMapper counterBusinessMapper,
                          CounterOperatorMapper counterOperatorMapper,
                          TicketMapper ticketMapper,
                          SysUserMapper sysUserMapper,
                          QueueService queueService,
                          AuthContextService authContextService,
                          PhoneCryptoService phoneCryptoService) {
        this.businessTypeService = businessTypeService;
        this.regionBusinessService = regionBusinessService;
        this.ticketService = ticketService;
        this.regionService = regionService;
        this.businessTypeMapper = businessTypeMapper;
        this.analyticsMapper = analyticsMapper;
        this.counterMapper = counterMapper;
        this.counterBusinessMapper = counterBusinessMapper;
        this.counterOperatorMapper = counterOperatorMapper;
        this.ticketMapper = ticketMapper;
        this.sysUserMapper = sysUserMapper;
        this.queueService = queueService;
        this.authContextService = authContextService;
        this.phoneCryptoService = phoneCryptoService;
    }

    // Business Types CRUD
    @GetMapping("/business-types")
    public Result<List<BusinessType>> listBusinessTypes(@RequestParam(required = false) Long regionId,
                                                        HttpServletRequest request) {
        if (regionId != null) {
            SysUser currentUser = authContextService.getCurrentUser(request);
            if (currentUser != null) {
                authContextService.assertRegionAccess(currentUser, regionId);
            }
            // 返回该区域关联的业务类型（用于窗口管理等场景）
            return Result.ok(regionBusinessService.listBusinessTypesByRegion(regionId));
        }
        return Result.ok(businessTypeService.listAll());
    }

    @GetMapping("/business-types/{id}")
    public Result<BusinessType> getBusinessType(@PathVariable Long id) {
        return Result.ok(businessTypeService.getById(id));
    }

    @PostMapping("/business-types")
    public Result<BusinessType> createBusinessType(@Valid @RequestBody BusinessType businessType) {
        return Result.ok(businessTypeService.create(businessType));
    }

    @PutMapping("/business-types/{id}")
    public Result<BusinessType> updateBusinessType(@PathVariable Long id, @RequestBody BusinessType businessType) {
        businessType.setId(id);
        return Result.ok(businessTypeService.update(businessType));
    }

    @DeleteMapping("/business-types/{id}")
    public Result<Void> deleteBusinessType(@PathVariable Long id) {
        businessTypeService.delete(id);
        return Result.ok();
    }

    // Counters CRUD
    @GetMapping("/counters")
    public Result<List<CounterDTO>> listCounters(@RequestParam(required = false) Long regionId,
                                                 HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        QueryWrapper<Counter> qw = new QueryWrapper<>();

        // 区域权限过滤
        Set<Long> allowedRegionIds = authContextService.resolveAllowedRegionIds(currentUser);
        if (regionId != null) {
            if (allowedRegionIds != null && !allowedRegionIds.contains(regionId)) {
                return Result.ok(Collections.emptyList());
            }
            qw.eq("region_id", regionId);
        } else if (allowedRegionIds != null && !allowedRegionIds.isEmpty()) {
            qw.in("region_id", allowedRegionIds);
        } else if (allowedRegionIds != null && allowedRegionIds.isEmpty()) {
            // 有权限限制但没有可用区域 → 返回空列表
            return Result.ok(Collections.emptyList());
        }

        // 窗口操作员只能看到自己被分配的窗口
        if ("WINDOW_OPERATOR".equals(currentUser.getRole())) {
            List<Long> assignedCounterIds = counterOperatorMapper.selectCounterIdsByUserId(currentUser.getId());
            if (assignedCounterIds == null || assignedCounterIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            qw.in("id", assignedCounterIds);
        }

        List<Counter> counters = counterMapper.selectList(qw);
        List<CounterDTO> dtos = counters.stream().map(c -> {
            // 状态恢复：柜台为 busy 但无有效服务中票号，自动恢复为 idle
            if (CounterStatus.BUSY.getValue().equals(c.getStatus())) {
                boolean needReset = true;
                if (c.getCurrentTicketId() != null) {
                    Ticket ticket = ticketMapper.selectById(c.getCurrentTicketId());
                    if (ticket != null && (TicketStatus.CALLED.getValue().equals(ticket.getStatus()) || TicketStatus.SERVING.getValue().equals(ticket.getStatus()))) {
                        needReset = false;
                    }
                }
                if (needReset) {
                    c.setStatus(CounterStatus.IDLE.getValue());
                    c.setCurrentTicketId(null);
                    counterMapper.updateById(c);
                }
            }
            CounterDTO dto = new CounterDTO();
            dto.setId(c.getId());
            dto.setRegionId(c.getRegionId());
            dto.setNumber(c.getNumber());
            dto.setName(c.getName());
            dto.setStatus(c.getStatus());
            dto.setOperatorName(c.getOperatorName());
            List<Long> btIds = counterBusinessMapper.selectBusinessTypeIdsByCounterId(c.getId());
            dto.setBusinessTypeIds(btIds);
            List<BusinessType> businessTypes = btIds.stream()
                .map(businessTypeMapper::selectById)
                .filter(bt -> bt != null)
                .collect(Collectors.toList());
            dto.setBusinessTypes(businessTypes);
            // 操作员信息
            List<Long> operatorIds = counterOperatorMapper.selectUserIdsByCounterId(c.getId());
            dto.setOperatorIds(operatorIds);
            List<SysUser> operators = counterOperatorMapper.selectOperatorsByCounterId(c.getId());
            dto.setOperatorNames(operators.stream().map(SysUser::getName).collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(dtos);
    }

    @GetMapping("/counters/{id}")
    public Result<CounterDTO> getCounter(@PathVariable Long id, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Counter c = counterMapper.selectById(id);
        if (c == null) return Result.error(400, "窗口不存在");
        authContextService.assertRegionAccess(currentUser, c.getRegionId());
        CounterDTO dto = new CounterDTO();
        dto.setId(c.getId());
        dto.setRegionId(c.getRegionId());
        dto.setNumber(c.getNumber());
        dto.setName(c.getName());
        dto.setStatus(c.getStatus());
        dto.setOperatorName(c.getOperatorName());
        dto.setBusinessTypeIds(counterBusinessMapper.selectBusinessTypeIdsByCounterId(id));
        List<BusinessType> businessTypes = dto.getBusinessTypeIds().stream()
            .map(businessTypeMapper::selectById)
            .filter(bt -> bt != null)
            .collect(Collectors.toList());
        dto.setBusinessTypes(businessTypes);
        // 操作员信息
        List<Long> operatorIds = counterOperatorMapper.selectUserIdsByCounterId(id);
        dto.setOperatorIds(operatorIds);
        List<SysUser> operators = counterOperatorMapper.selectOperatorsByCounterId(id);
        dto.setOperatorNames(operators.stream().map(SysUser::getName).collect(Collectors.toList()));
        return Result.ok(dto);
    }

    @PostMapping("/counters")
    @Transactional
    public Result<CounterDTO> createCounter(@RequestBody CounterDTO dto,
                                            HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        // 新增：校验用户是否有权在该区域创建
        authContextService.assertRegionAccess(currentUser, dto.getRegionId());
        createCounterInternal(dto);
        return Result.ok(dto);
    }

    @PutMapping("/counters/{id}")
    @Transactional
    public Result<Void> updateCounter(@PathVariable Long id, @RequestBody CounterDTO dto,
                                      HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Counter counter = counterMapper.selectById(id);
        if (counter == null) return Result.error(400, "窗口不存在");
        // 检查原区域权限
        authContextService.assertRegionAccess(currentUser, counter.getRegionId());
        authContextService.assertRegionAccess(currentUser, dto.getRegionId());

        counter.setRegionId(dto.getRegionId());
        counter.setNumber(dto.getNumber());
        counter.setName(dto.getName());
        counter.setOperatorName(dto.getOperatorName());
        if (dto.getStatus() != null) {
            counter.setStatus(dto.getStatus());
        }
        counterMapper.updateById(counter);

        // Delete and recreate business type associations
        counterBusinessMapper.delete(new QueryWrapper<CounterBusiness>().eq("counter_id", id));
        if (dto.getBusinessTypeIds() != null) {
            for (Long btId : dto.getBusinessTypeIds()) {
                if (businessTypeMapper.selectById(btId) == null) {
                    throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);
                }
                CounterBusiness cb = new CounterBusiness();
                cb.setCounterId(id);
                cb.setBusinessTypeId(btId);
                counterBusinessMapper.insert(cb);
            }
        }

        // Delete and recreate operator associations
        counterOperatorMapper.deleteByCounterId(id);
        if (dto.getOperatorIds() != null) {
            for (Long opUserId : dto.getOperatorIds()) {
                SysUser user = sysUserMapper.selectById(opUserId);
                if (user == null || !"WINDOW_OPERATOR".equals(user.getRole())) {
                    throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "无效的操作员: " + opUserId);
                }
                CounterOperator co = new CounterOperator();
                co.setCounterId(id);
                co.setUserId(opUserId);
                counterOperatorMapper.insert(co);
            }
        }
        return Result.ok();
    }

    @GetMapping("/counters/import-template")
    public void downloadCounterImportTemplate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        authContextService.requireCurrentUser(request);
        byte[] content = generateCounterImportTemplate();
        String filename = "counter_import_template.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        response.getOutputStream().write(content);
    }

    @PostMapping(value = "/counters/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public Result<CounterImportResult> importCounters(@RequestPart("file") MultipartFile file,
                                                      HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请先选择要导入的 Excel 文件");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet templateSheet = workbook.getSheet("窗口导入模板");
            if (templateSheet == null) {
                templateSheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            }
            if (templateSheet == null) {
                throw new BusinessException(400, "导入文件缺少数据工作表");
            }

            DataFormatter formatter = new DataFormatter();
            validateCounterImportHeader(templateSheet.getRow(0), formatter);

            List<BusinessType> businessTypes = businessTypeService.listAll();
            Map<String, BusinessType> businessTypeByName = businessTypes.stream()
                    .filter(bt -> bt.getName() != null && !bt.getName().isBlank())
                    .collect(Collectors.toMap(BusinessType::getName, bt -> bt, (left, right) -> left, LinkedHashMap::new));

            Set<String> batchRegionNumberKeys = new LinkedHashSet<>();
            int importedCount = 0;

            for (int rowIndex = 1; rowIndex <= templateSheet.getLastRowNum(); rowIndex++) {
                Row row = templateSheet.getRow(rowIndex);
                if (row == null || isCounterImportRowEmpty(row, formatter)) {
                    continue;
                }

                int displayRowNum = rowIndex + 1;
                String regionCode = formatter.formatCellValue(row.getCell(0)).trim();
                String numberText = formatter.formatCellValue(row.getCell(1)).trim();
                String counterName = formatter.formatCellValue(row.getCell(2)).trim();
                String businessTypeNames = formatter.formatCellValue(row.getCell(3)).trim();

                if (regionCode.isEmpty()) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行缺少所属区域区划代码");
                }
                if (numberText.isEmpty()) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行缺少窗口编号");
                }
                if (counterName.isEmpty()) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行缺少窗口名称");
                }
                if (businessTypeNames.isEmpty()) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行缺少支持业务类型");
                }

                Region region = regionService.getByCode(regionCode);
                if (region == null) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行所属区域区划代码不存在: " + regionCode);
                }
                authContextService.assertRegionAccess(currentUser, region.getId());

                Integer counterNumber;
                try {
                    counterNumber = Integer.valueOf(numberText);
                } catch (NumberFormatException e) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行窗口编号必须为整数");
                }
                if (counterNumber <= 0) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行窗口编号必须大于 0");
                }

                String batchKey = region.getId() + "#" + counterNumber;
                if (!batchRegionNumberKeys.add(batchKey)) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行窗口编号与同文件中的其他记录重复");
                }

                Counter existingCounter = counterMapper.selectOne(new QueryWrapper<Counter>()
                        .eq("region_id", region.getId())
                        .eq("number", counterNumber)
                        .last("LIMIT 1"));
                if (existingCounter != null) {
                    throw new BusinessException(400, "第 " + displayRowNum + " 行窗口编号已存在于该区域: " + counterNumber);
                }

                List<Long> businessTypeIds = parseBusinessTypeIds(displayRowNum, businessTypeNames, businessTypeByName);
                CounterDTO dto = new CounterDTO();
                dto.setRegionId(region.getId());
                dto.setNumber(counterNumber);
                dto.setName(counterName);
                dto.setBusinessTypeIds(businessTypeIds);
                dto.setOperatorIds(List.of());
                dto.setStatus(CounterStatus.IDLE.getValue());
                createCounterInternal(dto);
                importedCount++;
            }

            if (importedCount == 0) {
                throw new BusinessException(400, "导入文件中没有可导入的数据");
            }
            return Result.ok(new CounterImportResult(importedCount));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "读取 Excel 失败，请使用系统模板并上传 .xlsx 或 .xls 文件");
        }
    }

    @DeleteMapping("/counters/{id}")
    public Result<Void> deleteCounter(@PathVariable Long id,
                                      HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Counter counter = counterMapper.selectById(id);
        if (counter == null) return Result.error(400, "窗口不存在");
        // 检查区域权限
        authContextService.assertRegionAccess(currentUser, counter.getRegionId());
        if (!CounterStatus.IDLE.getValue().equals(counter.getStatus())) {
            return Result.error(ResultCode.COUNTER_NOT_OPERABLE);
        }
        // 物理删除（直接删除，不做软删除）
        counterMapper.physicalDeleteById(id);
        counterBusinessMapper.delete(new QueryWrapper<CounterBusiness>().eq("counter_id", id));
        counterOperatorMapper.deleteByCounterId(id);
        return Result.ok();
    }

    // Window stats detail API
    @GetMapping("/counters/{id}/stats")
    public Result<CounterStatsVO> getCounterStats(@PathVariable Long id) {
        Counter counter = counterMapper.selectById(id);
        if (counter == null) return Result.error(400, "窗口不存在");

        CounterStatsVO stats = new CounterStatsVO();
        stats.setCurrentStatus(counter.getStatus());

        // 当前服务中的票号
        if (counter.getCurrentTicketId() != null) {
            Ticket curTicket = ticketMapper.selectById(counter.getCurrentTicketId());
            if (curTicket != null) {
                stats.setCurrentTicketNo(curTicket.getTicketNo());
                BusinessType bt = businessTypeMapper.selectById(curTicket.getBusinessTypeId());
                stats.setCurrentBusinessTypeName(bt != null ? bt.getName() : "");
            }
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        CounterStatsSummaryRow summary = analyticsMapper.selectCounterStatsSummary(id, startOfDay, endOfDay);
        if (summary != null) {
            stats.setTodayServedCount(summary.getTodayServedCount() == null ? 0 : summary.getTodayServedCount().intValue());
            stats.setTodayCalledCount(summary.getTodayCalledCount() == null ? 0 : summary.getTodayCalledCount().intValue());
            stats.setTodaySkippedCount(summary.getTodaySkippedCount() == null ? 0 : summary.getTodaySkippedCount().intValue());
            stats.setAvgServiceMinutes(summary.getAvgServiceMinutes() == null ? 0.0 : summary.getAvgServiceMinutes());
            stats.setAvgWaitMinutes(summary.getAvgWaitMinutes() == null ? 0.0 : summary.getAvgWaitMinutes());
        }

        // 支持的业务类型及其等待人数
        List<Long> btIds = counterBusinessMapper.selectBusinessTypeIdsByCounterId(id);
        List<CounterStatsVO.BusinessWaitingInfo> waitingInfos = btIds.stream()
            .map(btId -> {
                BusinessType bt = businessTypeMapper.selectById(btId);
                if (bt == null) return null;
                CounterStatsVO.BusinessWaitingInfo info = new CounterStatsVO.BusinessWaitingInfo();
                info.setBusinessTypeId(btId);
                info.setBusinessTypeName(bt.getName());
                info.setPrefix(bt.getPrefix());
                info.setWaitingCount((int) queueService.getWaitingCount(counter.getRegionId(), btId));
                return info;
            })
            .filter(info -> info != null)
            .collect(Collectors.toList());
        stats.setWaitingByBusiness(waitingInfos);

        List<CounterRecentServiceRow> recentRows = analyticsMapper.selectCounterRecentServices(id, startOfDay, endOfDay, 10);
        List<CounterStatsVO.RecentServiceRecord> recentRecords = recentRows.stream().map(t -> {
            CounterStatsVO.RecentServiceRecord rec = new CounterStatsVO.RecentServiceRecord();
            rec.setTicketNo(t.getTicketNo());
            rec.setBusinessTypeName(t.getBusinessTypeName() != null ? t.getBusinessTypeName() : "");
            rec.setCustomerName(t.getCustomerName() != null ? t.getCustomerName() : "");
            rec.setStatus(t.getStatus());
            rec.setCalledAt(t.getCalledAt() != null ? t.getCalledAt().toString() : "");
            rec.setCompletedAt(t.getCompletedAt() != null ? t.getCompletedAt().toString() : "");
            rec.setServiceMinutes(t.getServiceMinutes() == null ? 0.0 : t.getServiceMinutes());
            return rec;
        }).collect(Collectors.toList());
        stats.setRecentServices(recentRecords);

        return Result.ok(stats);
    }

    // Get window operators by region
    @GetMapping("/operators")
    public Result<List<SysUser>> getOperatorsByRegion(@RequestParam Long regionId, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        authContextService.assertRegionAccess(currentUser, regionId);
        return Result.ok(sysUserMapper.selectByRegionIdAndRole(regionId, "WINDOW_OPERATOR"));
    }

    private void createCounterInternal(CounterDTO dto) {
        Counter counter = new Counter();
        counter.setRegionId(dto.getRegionId());
        counter.setNumber(dto.getNumber());
        counter.setName(dto.getName());
        counter.setStatus(CounterStatus.IDLE.getValue());
        counter.setOperatorName(dto.getOperatorName());
        counterMapper.insert(counter);

        if (dto.getBusinessTypeIds() != null) {
            for (Long btId : dto.getBusinessTypeIds()) {
                if (businessTypeMapper.selectById(btId) == null) {
                    throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);
                }
                CounterBusiness cb = new CounterBusiness();
                cb.setCounterId(counter.getId());
                cb.setBusinessTypeId(btId);
                counterBusinessMapper.insert(cb);
            }
        }

        if (dto.getOperatorIds() != null) {
            for (Long opUserId : dto.getOperatorIds()) {
                SysUser user = sysUserMapper.selectById(opUserId);
                if (user == null || !"WINDOW_OPERATOR".equals(user.getRole())) {
                    throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "无效的操作员: " + opUserId);
                }
                CounterOperator co = new CounterOperator();
                co.setCounterId(counter.getId());
                co.setUserId(opUserId);
                counterOperatorMapper.insert(co);
            }
        }

        dto.setId(counter.getId());
        dto.setStatus(CounterStatus.IDLE.getValue());
    }

    private byte[] generateCounterImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet templateSheet = workbook.createSheet("窗口导入模板");
            Row headerRow = templateSheet.createRow(0);
            for (int i = 0; i < COUNTER_IMPORT_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(COUNTER_IMPORT_HEADERS[i]);
                templateSheet.setColumnWidth(i, switch (i) {
                    case 0 -> 20 * 256;
                    case 1 -> 12 * 256;
                    case 2 -> 18 * 256;
                    default -> 42 * 256;
                });
            }

            Sheet instructionSheet = workbook.createSheet("填写说明");
            String[][] instructions = {
                    {"字段", "说明"},
                    {"所属区域区划代码", "必填，填写系统中已存在的区域区划代码。"},
                    {"窗口编号", "必填，填写正整数，同一区域内不能重复。"},
                    {"窗口名称", "必填，例如：1号窗口。"},
                    {"支持业务类型", "必填，填写系统当前已存在的业务类型名称，多个业务用中文逗号、英文逗号、分号或顿号分隔。"},
                    {"导入规则", "导入仅新增窗口，不会覆盖已有窗口；新增窗口默认状态为空闲。"},
                    {"示例", "440300 | 1 | 1号窗口 | 开户, 取号咨询"},
                    {"示例", "440305 | 2 | 南山综合窗口 | 社保办理，证明打印"}
            };
            for (int i = 0; i < instructions.length; i++) {
                Row row = instructionSheet.createRow(i);
                row.createCell(0).setCellValue(instructions[i][0]);
                row.createCell(1).setCellValue(instructions[i][1]);
            }
            instructionSheet.setColumnWidth(0, 20 * 256);
            instructionSheet.setColumnWidth(1, 88 * 256);

            Sheet businessTypeSheet = workbook.createSheet("当前业务类型");
            String[] businessHeaders = {"业务类型名称", "前缀", "状态", "描述"};
            Row businessHeaderRow = businessTypeSheet.createRow(0);
            for (int i = 0; i < businessHeaders.length; i++) {
                businessHeaderRow.createCell(i).setCellValue(businessHeaders[i]);
                businessTypeSheet.setColumnWidth(i, switch (i) {
                    case 0 -> 24 * 256;
                    case 1 -> 12 * 256;
                    case 2 -> 12 * 256;
                    default -> 42 * 256;
                });
            }
            List<BusinessType> businessTypes = businessTypeService.listAll();
            for (int i = 0; i < businessTypes.size(); i++) {
                BusinessType businessType = businessTypes.get(i);
                Row row = businessTypeSheet.createRow(i + 1);
                row.createCell(0).setCellValue(Objects.toString(businessType.getName(), ""));
                row.createCell(1).setCellValue(Objects.toString(businessType.getPrefix(), ""));
                row.createCell(2).setCellValue(Boolean.TRUE.equals(businessType.getIsEnabled()) ? "启用" : "停用");
                row.createCell(3).setCellValue(Objects.toString(businessType.getDescription(), ""));
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "生成导入模板失败");
        }
    }

    private void validateCounterImportHeader(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new BusinessException(400, "导入文件缺少表头，请先下载模板");
        }
        for (int i = 0; i < COUNTER_IMPORT_HEADERS.length; i++) {
            String actual = formatter.formatCellValue(headerRow.getCell(i)).trim();
            if (!COUNTER_IMPORT_HEADERS[i].equals(actual)) {
                throw new BusinessException(400, "模板表头不正确，请先下载最新模板");
            }
        }
    }

    private boolean isCounterImportRowEmpty(Row row, DataFormatter formatter) {
        for (int i = 0; i < COUNTER_IMPORT_HEADERS.length; i++) {
            if (!formatter.formatCellValue(row.getCell(i)).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private List<Long> parseBusinessTypeIds(int displayRowNum,
                                            String businessTypeNames,
                                            Map<String, BusinessType> businessTypeByName) {
        List<Long> businessTypeIds = new ArrayList<>();
        Set<Long> seenIds = new LinkedHashSet<>();
        String[] tokens = businessTypeNames.split("[,，;；、\\n]+");
        for (String token : tokens) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            BusinessType businessType = businessTypeByName.get(name);
            if (businessType == null) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行存在无效业务类型: " + name);
            }
            if (seenIds.add(businessType.getId())) {
                businessTypeIds.add(businessType.getId());
            }
        }
        if (businessTypeIds.isEmpty()) {
            throw new BusinessException(400, "第 " + displayRowNum + " 行缺少有效的支持业务类型");
        }
        return businessTypeIds;
    }

    // Ticket list for admin
    @GetMapping("/tickets")
    public Result<List<AdminTicketVO>> listTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String ticketNo,
            HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Set<Long> allowedRegionIds = authContextService.resolveAllowedRegionIds(currentUser);
        return Result.ok(ticketService.listTickets(status, date, startDate, endDate, phone, name, ticketNo, allowedRegionIds));
    }

    @GetMapping("/tickets/{id}/phone")
    public Result<java.util.Map<String, String>> getTicketPhone(@PathVariable Long id, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            return Result.error(ResultCode.TICKET_NOT_FOUND);
        }
        authContextService.assertRegionAccess(currentUser, ticket.getRegionId());
        if (ticket.getPhoneCiphertext() == null || ticket.getPhoneCiphertext().isBlank()) {
            return Result.error(400, "该记录缺少可解密手机号");
        }
        return Result.ok(java.util.Map.of(
                "phone", phoneCryptoService.decrypt(ticket.getPhoneCiphertext()),
                "maskedPhone", ticket.getPhoneMasked() != null ? ticket.getPhoneMasked() : ticket.getPhone()
        ));
    }

    // Business type detail stats: region + counter + operator + ticket count
    @GetMapping("/business-types/{id}/detail")
    public Result<List<BusinessTypeDetailVO>> getBusinessTypeDetail(@PathVariable Long id,
                                                                    HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Set<Long> allowedRegionIds = authContextService.resolveAllowedRegionIds(currentUser);
        return Result.ok(businessTypeService.getBusinessTypeDetail(id, allowedRegionIds));
    }
}
