package com.car.order.web.service.technicianappointment.impl;

import com.car.account.client.enums.comm.TerminalEnum;
import com.car.account.client.feign.TechnicianFegin;
import com.car.account.client.feign.VehicleFegin;
import com.car.account.client.response.technician.TechnicianRes;
import com.car.account.client.response.vehicle.config.ConfigRes;
import com.car.account.client.response.vehicle.vehicleUser.VehicleUserRes;
import com.car.common.enums.OrderPrefixEnum;
import com.car.common.enums.ResEnum;
import com.car.common.enums.StsEnum;
import com.car.common.enums.UserTypeEnum;
import com.car.common.exception.BusinessException;
import com.car.common.res.PageRes;
import com.car.common.res.ResultRes;
import com.car.common.utils.DigitUtils;
import com.car.common.utils.OrderUtils;
import com.car.common.utils.TokenHelper;
import com.car.common.utils.UuidUtils;
import com.car.order.client.enums.consult.OrderTypeEnum;
import com.car.order.client.enums.goods.OrderStsEnum;
import com.car.order.client.enums.sharetechnicianorder.ShareTechnicianOrderEnum;
import com.car.order.client.request.order.order.AddOrderInfoReq;
import com.car.order.client.request.order.order.ConfirmOrderReq;
import com.car.order.client.request.technicianappointment.QueryShareTechnicianOrderReq;
import com.car.order.client.request.technicianappointment.ShareTechnicianOrderReq;
import com.car.order.client.request.technicianappointment.UpdateShareTechnicianOrderReq;
import com.car.order.client.response.technicianappointment.ShareTechnicianOrderInfoRes;
import com.car.order.client.response.technicianappointment.ShareTechnicianOrderRes;
import com.car.order.client.response.technicianappointment.TechnicianBrandRes;
import com.car.order.web.mapper.order.OrderInfoMapper;
import com.car.order.web.mapper.sharetechnicianorder.ShareTechnicianOrderMapper;
import com.car.order.web.model.order.OrderInfo;
import com.car.order.web.model.sharetechnicianorder.ShareTechnicianOrder;
import com.car.order.web.service.order.OrderInfoService;
import com.car.order.web.service.technicianappointment.ShareTechnicianOrderService;
import com.car.system.client.feign.SystemFeign;
import com.car.system.client.response.dict.DictionaryRes;
import com.car.utility.client.enums.OrderStatusEnum;
import com.car.utility.client.enums.PaymentTypeEnum;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author zhoujian
 * @PACKAGE_NAME: com.car.order.web.service.technicianappointment.impl
 * @NAME: TechnicianAppointmentOrderServiceImpl
 * @DATE: 2021/3/4 21:02
 */
@Slf4j
@Service
public class ShareTechnicianOrderServiceImpl implements ShareTechnicianOrderService {

    @Autowired
    ShareTechnicianOrderMapper shareTechnicianOrderMapper;

    @Autowired
    OrderInfoMapper orderInfoMapper;

    @Autowired
    SystemFeign systemFeign;

    @Autowired
    TechnicianFegin technicianFegin;

    @Autowired
    VehicleFegin vehicleFegin;
    @Autowired
    OrderInfoService orderInfoService;

    /**
     * ????????????????????????
     */
    private static final String default_share_technician_money = "6005";

    /**
     * ???????????????????????????
     */
    private static final String default_share_technician_service_money = "6007";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultRes<String> saveTechnicianAppointment(ShareTechnicianOrderReq req) {
        ShareTechnicianOrder shareTechnicianOrder = new ShareTechnicianOrder();
        BeanUtils.copyProperties(req, shareTechnicianOrder);
        // ???????????????????????????????????????
        shareTechnicianOrder.setOwnerUuid(TokenHelper.getUserUuid());
        shareTechnicianOrder.setUuid(UuidUtils.getUuid());
        shareTechnicianOrder.setCreatedBy(TokenHelper.getUserName());
        shareTechnicianOrder.setCreatedTime(new Date());
        shareTechnicianOrder.setLastUpdatedBy(TokenHelper.getUserName());
        shareTechnicianOrder.setLastUpdatedTime(new Date());
        shareTechnicianOrder.setSts(StsEnum.ACTIVE.getValue());
        shareTechnicianOrder.setOrderStatus(ShareTechnicianOrderEnum.WaitingForOrder.getValue());

        //????????????????????????
        ResultRes<DictionaryRes> reservationMoneyRes = systemFeign.queryByUuid(StringUtils.isEmpty(req.getReservationMoneyUuid())?default_share_technician_money:req.getReservationMoneyUuid());
        if (!reservationMoneyRes.isSuccess()){
            return ResultRes.error(ResEnum.TECHNICIAN_NO_CONSUL_ORDER);
        }
        DictionaryRes reservationMoney = reservationMoneyRes.getData();
        BigDecimal reservationMoneyNum = new BigDecimal(reservationMoney.getLableValue());
        //????????????????????????
        shareTechnicianOrder.setReservationMoney(reservationMoneyNum);
        //????????????????????????
        ResultRes<DictionaryRes> platformMoneyRes = systemFeign.queryByUuid(default_share_technician_service_money);
        if (!platformMoneyRes.isSuccess()){
            return ResultRes.error(ResEnum.TECHNICIAN_NO_CONSUL_ORDER);
        }
        DictionaryRes platformMoney = platformMoneyRes.getData();
        BigDecimal platformMoneyNum = new BigDecimal(platformMoney.getLableValue());
        //????????????????????????
        shareTechnicianOrder.setPlatformMoney(platformMoneyNum);
        //?????????????????????
        shareTechnicianOrder.setPayNum(DigitUtils.add(reservationMoneyNum,platformMoneyNum));
        // ?????????????????????????????????
        shareTechnicianOrder.setOrderNum(OrderUtils.GenOrderNo(OrderPrefixEnum.GX));

        int cnt = shareTechnicianOrderMapper.insert(shareTechnicianOrder);
        if (cnt > 0) {
            //?????????????????????????????????????????????
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setUuid(UuidUtils.getUuid());
            orderInfo.setOrderType(OrderTypeEnum.SHARED_TECHNICIAN.getValue());
            orderInfo.setOrderUuid(shareTechnicianOrder.getUuid());
            orderInfo.setPaySts(OrderStsEnum.UNPAID.getValue());
            orderInfo.setCreatedBy(TokenHelper.getUserName());
            orderInfo.setCreatedTime(new Date());
            orderInfo.setLastUpdatedBy(TokenHelper.getUserName());
            orderInfo.setLastUpdatedTime(new Date());
            orderInfo.setSts(StsEnum.ACTIVE.getValue());
            int orderCnt = orderInfoMapper.insert(orderInfo);
            if (orderCnt > 0) {
                return ResultRes.success(shareTechnicianOrder.getUuid());
            } else {
                log.error("???????????????????????????????????????????????????????????????");
                throw new BusinessException(ResEnum.INSERT_ORDER_ERROR);
            }
        } else {
            return ResultRes.error(ResEnum.INSERT_ORDER_ERROR);
        }
    }

    @Override
    public PageRes<List<ShareTechnicianOrderRes>> queryShareTechnicianOrderList(QueryShareTechnicianOrderReq req) {
        PageHelper.startPage(req.getPageNum(), req.getPageSize());
        List<ShareTechnicianOrder> list = null;
        List<Integer> orderStatus = new ArrayList<>();
        //?????????(1)?????????(2)?????????(3)??????(4,5)

        /**
         * ????????? ?????????????????????(?????????????????????)?????????????????????/??????(?????????????????????)
         * ????????? ??????????????????????????????????????????/??????(?????????????????????)
         * ??????????????????
         * 1????????????
         * 2????????????
         * 3????????????
         * 4????????????
         * 5????????????
         * 6????????????
         */
        if (StringUtils.isEmpty(TokenHelper.getUserType())){

            list = shareTechnicianOrderMapper.queryShareTechnicianOrderList(null, null, null);

            PageInfo<ShareTechnicianOrder> pageInfo = new PageInfo(list);
            List<ShareTechnicianOrderRes> resList = new ArrayList<>();
            if (!CollectionUtils.isEmpty(list)) {
                list.stream().forEach(data -> {
                    ShareTechnicianOrderRes res = new ShareTechnicianOrderRes();
                    BeanUtils.copyProperties(data, res);
                    res.setOrderStatusName(ShareTechnicianOrderEnum.enumOfDesc(data.getOrderStatus()));
                    resList.add(res);
                });
            }
            return PageRes.success(resList, pageInfo.getPageSize(), (int) pageInfo.getTotal(), pageInfo.getPages());
        }
        if (UserTypeEnum.vehicle.getType().equals(TokenHelper.getUserType().intValue())) {
            //???????????????
            if (ShareTechnicianOrderEnum.WaitingForOrder.getValue().equals(req.getOrderStatus())) {
                orderStatus.add(ShareTechnicianOrderEnum.WaitingForOrder.getValue());
            } else if (ShareTechnicianOrderEnum.SuccessfullyReceivedTheOrder.getValue().equals(req.getOrderStatus())) {
                orderStatus.add(ShareTechnicianOrderEnum.PendingOrder.getValue());
                orderStatus.add(ShareTechnicianOrderEnum.SuccessfullyReceivedTheOrder.getValue());
            } else if (ShareTechnicianOrderEnum.CancelTheOrder.getValue().equals(req.getOrderStatus())) {
                orderStatus.add(ShareTechnicianOrderEnum.CancelTheOrder.getValue());
            } else if (ShareTechnicianOrderEnum.OrderCompleted.getValue().equals(req.getOrderStatus())) {
                orderStatus.add(ShareTechnicianOrderEnum.OrderCompleted.getValue());
                orderStatus.add(ShareTechnicianOrderEnum.Refunded.getValue());
            }
        } else if (UserTypeEnum.technician.getType().equals(TokenHelper.getUserType().intValue())) {
            //???????????????
            orderStatus.add(ShareTechnicianOrderEnum.PendingOrder.getValue());
            orderStatus.add(ShareTechnicianOrderEnum.SuccessfullyReceivedTheOrder.getValue());
            orderStatus.add(ShareTechnicianOrderEnum.CancelTheOrder.getValue());
            orderStatus.add(ShareTechnicianOrderEnum.OrderCompleted.getValue());
            orderStatus.add(ShareTechnicianOrderEnum.Refunded.getValue());

            /*if (ShareTechnicianOrderEnum.PendingOrder.getValue().equals(req.getOrderStatus())) {
                orderStatus.add(ShareTechnicianOrderEnum.PendingOrder.getValue());
            } else if (ShareTechnicianOrderEnum.SuccessfullyReceivedTheOrder.getValue().equals(req.getOrderStatus())) {
                orderStatus.add(ShareTechnicianOrderEnum.SuccessfullyReceivedTheOrder.getValue());
            } else if (ShareTechnicianOrderEnum.CancelTheOrder.getValue().equals(req.getOrderStatus())) {
                orderStatus.add(ShareTechnicianOrderEnum.CancelTheOrder.getValue());
            } else if (ShareTechnicianOrderEnum.OrderCompleted.getValue().equals(req.getOrderStatus())) {
                orderStatus.add(ShareTechnicianOrderEnum.OrderCompleted.getValue());
                orderStatus.add(ShareTechnicianOrderEnum.Refunded.getValue());
            }*/
        }

        list = shareTechnicianOrderMapper.queryShareTechnicianOrderList(orderStatus, TokenHelper.getUserUuid(), TokenHelper.getUserType());

        PageInfo<ShareTechnicianOrder> pageInfo = new PageInfo(list);
        List<ShareTechnicianOrderRes> resList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(list)) {
            list.stream().forEach(data -> {
                ShareTechnicianOrderRes res = new ShareTechnicianOrderRes();
                BeanUtils.copyProperties(data, res);
                res.setOrderStatusName(ShareTechnicianOrderEnum.enumOfDesc(data.getOrderStatus()));
                resList.add(res);
            });
        }
        return PageRes.success(resList, pageInfo.getPageSize(), (int) pageInfo.getTotal(), pageInfo.getPages());
    }

    @Override
    public ResultRes updateShareTechnicianOrder(String uuid) {
        ShareTechnicianOrder shareTechnicianOrder = shareTechnicianOrderMapper.selectByPrimaryKey(uuid);
        if (StringUtils.isEmpty(shareTechnicianOrder)) {
            return ResultRes.error(ResEnum.NOT_ORDER_ERROR);
        }
        if (UserTypeEnum.vehicle.getType().equals(TokenHelper.getUserType())) {
            // ??????????????????
            if (ShareTechnicianOrderEnum.SuccessfullyReceivedTheOrder.getValue().equals(shareTechnicianOrder.getOrderStatus())) {
                //????????????????????????
                ConfirmOrderReq req = new ConfirmOrderReq();
                req.setOrderUuid(uuid);
                req.setOrderType(OrderTypeEnum.SHARED_TECHNICIAN.getValue());
                orderInfoService.confirmOrder(req);
                /*shareTechnicianOrder.setOrderStatus(ShareTechnicianOrderEnum.CancelTheOrder.getValue());
                shareTechnicianOrder.setLastUpdatedBy(TokenHelper.getUserName());
                shareTechnicianOrder.setLastUpdatedTime(new Date());
                shareTechnicianOrderMapper.updateByPrimaryKeySelective(shareTechnicianOrder);*/
                return ResultRes.success();
            } else {
                return ResultRes.error(ResEnum.LOGIN_TERMINAL_UPDATE_ERROR);
            }
        } else {
            return ResultRes.error(ResEnum.LOGIN_TERMINAL_UPDATE_ERROR);
        }
    }

    @Override
    public ResultRes applicationRefundShareTechnicianOrder(String uuid) {
        ShareTechnicianOrder shareTechnicianOrder = shareTechnicianOrderMapper.selectByPrimaryKey(uuid);
        if (StringUtils.isEmpty(shareTechnicianOrder)) {
            return ResultRes.error(ResEnum.NOT_ORDER_ERROR);
        }
        if (UserTypeEnum.vehicle.getType().equals(TokenHelper.getUserType())) {
            // ??????????????????
            //??????????????? ?????????????????????????????????????????????
            if (ShareTechnicianOrderEnum.SuccessfullyReceivedTheOrder.getValue().equals(shareTechnicianOrder.getOrderStatus()) || ShareTechnicianOrderEnum.PendingOrder.getValue().equals(shareTechnicianOrder.getOrderStatus())) {
                shareTechnicianOrder.setOrderStatus(ShareTechnicianOrderEnum.OrderCompleted.getValue());
                shareTechnicianOrder.setLastUpdatedBy(TokenHelper.getUserName());
                shareTechnicianOrder.setLastUpdatedTime(new Date());
                shareTechnicianOrderMapper.updateByPrimaryKeySelective(shareTechnicianOrder);
                // todo ???????????????????????????????????????????????????????????????
                return ResultRes.success();
            } else {
                return ResultRes.error(ResEnum.LOGIN_TERMINAL_UPDATE_ERROR);
            }
        } else {
            return ResultRes.error(ResEnum.LOGIN_TERMINAL_UPDATE_ERROR);
        }
    }

    @Override
    public ResultRes<ShareTechnicianOrderInfoRes> queryShareTechnicianOrder(String uuid) {
        ShareTechnicianOrder shareTechnicianOrder = shareTechnicianOrderMapper.selectByPrimaryKey(uuid);
        if (StringUtils.isEmpty(shareTechnicianOrder)) {
            return ResultRes.error(ResEnum.NOT_ORDER_ERROR);
        }
        //??????????????????
        ResultRes<TechnicianRes> resResultRes = technicianFegin.queryTechnicianDetail(shareTechnicianOrder.getTechnicianUuid());
        if (!resResultRes.isSuccess()){
            throw new BusinessException(resResultRes.getCode(),resResultRes.getMsg());
        }
        TechnicianRes technicianRes = resResultRes.getData();

        ResultRes<DictionaryRes> dictionaryResResultRes = systemFeign.queryByUuid(technicianRes.getTechnologyType());
        if (!dictionaryResResultRes.isSuccess()){
            throw new BusinessException(dictionaryResResultRes.getCode(),dictionaryResResultRes.getMsg());
        }

        ResultRes<VehicleUserRes> vehicleUserResResultRes = vehicleFegin.queryDetail(shareTechnicianOrder.getOwnerUuid());
        if (!vehicleUserResResultRes.isSuccess()){
            throw new BusinessException(vehicleUserResResultRes.getCode(),vehicleUserResResultRes.getMsg());
        }

        ShareTechnicianOrderInfoRes res = new ShareTechnicianOrderInfoRes();
        BeanUtils.copyProperties(shareTechnicianOrder, res);
        res.setName(technicianRes.getUserName());
        res.setPhotoImgUrl(technicianRes.getPhotoImgUrl());
        res.setTechnologyType(technicianRes.getTechnologyType());
        res.setWorkingYear(technicianRes.getWorkingYear());
        res.setShareNum(technicianRes.getShareNum());
        //????????????????????????
        res.setTechnicianPhone(technicianRes.getMobile());
        //????????????????????????
        res.setCarOwnerPhone( vehicleUserResResultRes.getData().getMobile());

        res.setTechnologyTypeName(dictionaryResResultRes.getData().getLableDesc());

        // ????????????????????????????????????
        ResultRes<ConfigRes> brandRes = vehicleFegin.queryConfig(shareTechnicianOrder.getBrandUuid());
        if (!brandRes.isSuccess()){
            throw new BusinessException(brandRes.getCode(),brandRes.getMsg());
        }
        res.setBrandName(brandRes.getData().getConfigName());
        ResultRes<ConfigRes> modelRes = vehicleFegin.queryConfig(shareTechnicianOrder.getModelUuid());
        if (!modelRes.isSuccess()){
            throw new BusinessException(modelRes.getCode(),modelRes.getMsg());
        }
        res.setModelName(modelRes.getData().getConfigName());

        //??????????????????????????????
        List<TechnicianBrandRes> technicianBrandResList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(technicianRes.getBrandList())) {
            technicianRes.getBrandList().forEach(data -> {
                TechnicianBrandRes technicianBrandRes = new TechnicianBrandRes();
                BeanUtils.copyProperties(data, technicianBrandRes);
                technicianBrandResList.add(technicianBrandRes);
            });
        }
        res.setBrandList(technicianBrandResList);
        return ResultRes.success(res);
    }

    @Override
    public ResultRes receiveShareTechnicianOrder(String uuid) {
        ShareTechnicianOrder shareTechnicianOrder = shareTechnicianOrderMapper.selectByPrimaryKey(uuid);
        if (StringUtils.isEmpty(shareTechnicianOrder)) {
            return ResultRes.error(ResEnum.NOT_ORDER_ERROR);
        }
        if (UserTypeEnum.technician.getType().equals(TokenHelper.getUserType())) {
            // ??????????????????
            if (ShareTechnicianOrderEnum.PendingOrder.getValue().equals(shareTechnicianOrder.getOrderStatus())) {
                shareTechnicianOrder.setOrderStatus(ShareTechnicianOrderEnum.SuccessfullyReceivedTheOrder.getValue());
                shareTechnicianOrder.setLastUpdatedBy(TokenHelper.getUserName());
                shareTechnicianOrder.setLastUpdatedTime(new Date());
                shareTechnicianOrderMapper.updateByPrimaryKeySelective(shareTechnicianOrder);
                // todo ???????????????????????????????????????????????????????????????
                return ResultRes.success();
            } else if (ShareTechnicianOrderEnum.OrderCompleted.getValue().equals(shareTechnicianOrder.getOrderStatus())){
                return ResultRes.error(ResEnum.TERMINAL_UPDATE_ERROR);
            }else {
                return ResultRes.error(ResEnum.LOGIN_TERMINAL_UPDATE_ERROR);
            }
        } else {
            return ResultRes.error(ResEnum.LOGIN_TERMINAL_UPDATE_ERROR);
        }
    }
}
