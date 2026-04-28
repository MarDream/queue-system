package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.AppointmentRequest;
import com.queue.dto.AppointmentResponse;
import com.queue.entity.Appointment;
import com.queue.entity.BusinessType;
import com.queue.mapper.AppointmentMapper;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.service.AppointmentService;
import com.queue.service.PhoneCryptoService;
import com.queue.util.PhoneUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final BusinessTypeMapper businessTypeMapper;
    private final PhoneCryptoService phoneCryptoService;

    public AppointmentServiceImpl(AppointmentMapper appointmentMapper,
                                  BusinessTypeMapper businessTypeMapper,
                                  PhoneCryptoService phoneCryptoService) {
        this.appointmentMapper = appointmentMapper;
        this.businessTypeMapper = businessTypeMapper;
        this.phoneCryptoService = phoneCryptoService;
    }

    @Override
    public List<AppointmentResponse> listAppointments(AppointmentRequest request) {
        QueryWrapper<Appointment> wrapper = new QueryWrapper<>();
        if (request.getBusinessTypeId() != null) {
            wrapper.eq("business_type_id", request.getBusinessTypeId());
        }
        if (request.getAppointmentDate() != null) {
            wrapper.eq("appointment_date", request.getAppointmentDate());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (PhoneUtil.looksLikeCompletePhone(request.getPhone())) {
                String phoneHash = phoneCryptoService.hash(request.getPhone());
                String maskedPhone = PhoneUtil.mask(request.getPhone());
                wrapper.and(w -> w.eq("phone_hash", phoneHash)
                        .or()
                        .isNull("phone_hash")
                        .eq("phone", maskedPhone));
            } else if (request.getPhone().matches("\\d{4}")) {
                wrapper.eq("phone_last4", request.getPhone());
            } else {
                wrapper.like("phone_masked", request.getPhone());
            }
        }
        wrapper.orderByDesc("created_at");

        List<Appointment> appointments = appointmentMapper.selectList(wrapper);
        return appointments.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    public AppointmentResponse createAppointment(AppointmentRequest request) {
        // 验证业务类型
        BusinessType bt = businessTypeMapper.selectById(request.getBusinessTypeId());
        if (bt == null || bt.getIsEnabled() == null || !bt.getIsEnabled()) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);
        }

        // 生成预约编号
        String appointmentNo = generateAppointmentNo(bt.getPrefix());
        PhoneCryptoService.ProtectedPhone protectedPhone = phoneCryptoService.protect(request.getPhone());

        Appointment appointment = new Appointment();
        appointment.setBusinessTypeId(request.getBusinessTypeId());
        appointment.setPhone(protectedPhone.masked());
        appointment.setPhoneCiphertext(protectedPhone.ciphertext());
        appointment.setPhoneHash(protectedPhone.hash());
        appointment.setPhoneMasked(protectedPhone.masked());
        appointment.setPhoneLast4(protectedPhone.last4());
        appointment.setPhoneKeyVersion(protectedPhone.keyVersion());
        appointment.setName(request.getName());
        appointment.setDate(request.getAppointmentDate());
        appointment.setTimeSlot(request.getTimeSlot());
        appointment.setStatus("pending");

        appointmentMapper.insert(appointment);

        AppointmentResponse response = convertToResponse(appointment);
        response.setAppointmentNo(appointmentNo);
        return response;
    }

    @Override
    public void cancelAppointment(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BusinessException(ResultCode.TICKET_NOT_FOUND);
        }

        appointment.setStatus("cancelled");
        appointmentMapper.updateById(appointment);
    }

    private String generateAppointmentNo(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uuid = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "Y" + prefix + date + uuid;
    }

    private AppointmentResponse convertToResponse(Appointment appointment) {
        AppointmentResponse response = new AppointmentResponse();
        response.setId(appointment.getId());
        response.setPhone(appointment.getPhoneMasked() != null ? appointment.getPhoneMasked() : appointment.getPhone());
        response.setName(appointment.getName());
        response.setAppointmentDate(appointment.getDate() != null ?
            appointment.getDate().toString() : null);
        response.setTimeSlot(appointment.getTimeSlot());
        response.setStatus(appointment.getStatus());

        BusinessType bt = businessTypeMapper.selectById(appointment.getBusinessTypeId());
        if (bt != null) {
            response.setBusinessType(bt.getName());
        }

        return response;
    }
}
