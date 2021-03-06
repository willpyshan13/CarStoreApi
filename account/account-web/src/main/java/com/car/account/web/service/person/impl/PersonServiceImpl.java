package com.car.account.web.service.person.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.car.account.client.enums.comm.TerminalEnum;
import com.car.account.client.enums.vehicle.VehicleUserTypeEnum;
import com.car.account.client.request.login.LoginReq;
import com.car.account.client.response.login.LoginRes;
import com.car.account.web.common.utils.ConfigConsts;
import com.car.account.web.common.utils.RandomUtil;
import com.car.account.web.mapper.store.StoreMapper;
import com.car.account.web.mapper.store.StoreUserMapper;
import com.car.account.web.mapper.technician.TechnicianMapper;
import com.car.account.web.mapper.vehicle.VehicleUserMapper;
import com.car.account.web.model.store.Store;
import com.car.account.web.model.store.StoreUser;
import com.car.account.web.model.technician.Technician;
import com.car.account.web.model.vehicle.VehicleUser;
import com.car.account.web.service.person.PersonService;
import com.car.common.enums.ResEnum;
import com.car.common.enums.StsEnum;
import com.car.common.enums.UserTypeEnum;
import com.car.common.exception.BusinessException;
import com.car.common.res.ResultRes;
import com.car.common.utils.*;
import com.car.common.utils.token.LoginToken;
import com.car.common.utils.token.TokenUtil;
import com.car.utility.client.feign.SmsFeign;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author zhangyp
 * @date 2021/1/16 21:28
 */
@Slf4j
@Service
public class PersonServiceImpl implements PersonService {

    @Resource
    RedisUtils redisUtils;
    @Resource
    private StoreUserMapper storeUserMapper;
    @Resource
    private StoreMapper storeMapper;
    @Resource
    private TechnicianMapper technicianMapper;
    @Resource
    private VehicleUserMapper vehicleUserMapper;
    @Resource
    private SmsFeign smsFeign;

    @Autowired
    ConfigConsts configConsts;

    /**
     * ?????????????????????
     * @param accountName
     * @param request
     * @return
     */
    @Override
    public ResultRes getLoginCode(String accountName,String terminal, HttpServletRequest request) {
        if(StringUtils.isEmpty(TerminalEnum.enumOfDesc(terminal))){
            //???????????????????????????????????????
            throw new BusinessException(ResEnum.LOGIN_TERMINAL_ERROR);
        }
        //???????????????????????????????????????1????????????10??????????????????
        checkSendRate(request,"getLoginCode");

        //??????redis????????????????????????????????????
        String cacheKey = String.format(Constants.LOGIN_VERIFICATION_CODE_CACHE_KEY, new Object[] { accountName });
        String redisCode = (String) redisUtils.get(cacheKey);
        if(!StringUtils.isEmpty(redisCode)){
            //?????????????????????????????????
            throw new BusinessException(ResEnum.REPEAT_SEND_MSG_ERROR.getValue(),ResEnum.REPEAT_SEND_MSG_ERROR.getDesc());
        }
        String mobile = null;
        //???????????????????????????
        if(TerminalEnum.VEHICLE.getValue().equals(terminal)){
            //????????????????????????????????????????????????????????????????????????????????????
            mobile = accountName;
        }else{
            //????????????/????????????????????????
            Technician technician = new Technician();
            technician.setMobile(accountName);
            technician.setSts(StsEnum.ACTIVE.getValue());
            technician = technicianMapper.selectOne(technician);
            if(!StringUtils.isEmpty(technician)){
                mobile = technician .getMobile();
            }else{
                //????????????????????????
                StoreUser store = new StoreUser();
                store.setMobile(accountName);
                store.setSts(StsEnum.ACTIVE.getValue());
                store = storeUserMapper.selectOne(store);
                if(StringUtils.isEmpty(store)){
                    throw new BusinessException(ResEnum.LOGIN_ACCOUNT_ERROR);
                }
                mobile = store .getMobile();
            }
        }
        //?????????????????????
        String smsCode = RandomUtil.getRandom(6);
        if(configConsts.getSmsSwitch().equals("close")){
            smsCode = "888888";
        }
        log.info("??????????????????????????????{}??????????????????{}",mobile,smsCode);
        smsFeign.sendRegister(mobile,smsCode);
        //?????????????????????redis??????????????????1??????
        redisUtils.set(cacheKey,smsCode,(long)Constants.LOGIN_VERIFICATION_CODE_TIME, TimeUnit.MINUTES);
        return ResultRes.success();
    }

    @Override
    public ResultRes<LoginRes> userLogin(LoginReq param, HttpServletRequest request) {
        if(StringUtils.isEmpty(TerminalEnum.enumOfDesc(param.getTerminal()))){
            //???????????????
            throw new BusinessException(ResEnum.LOGIN_TERMINAL_ERROR);
        }
        
        log.info("????????????????????????{}",JSONObject.toJSONString(param));
        
        //??????????????????
        validUserLogin(param);
        //??????????????????????????????
        String accountName = param.getAccountName();
        LoginRes tokenRes = null;
        if(TerminalEnum.VEHICLE.getValue().equals(param.getTerminal())){
            //??????????????????????????????
            VehicleUser vehicle = getVehicleUser(accountName);
            if(StringUtils.isEmpty(vehicle)){
                //??????????????????
                vehicle = new VehicleUser();
                vehicle.setUuid(UuidUtils.getUuid());
                //?????????????????????
                vehicle.setUserType(VehicleUserTypeEnum.REGISTER.getValue());
                vehicle.setMobile(accountName);
                vehicle.setSts(StsEnum.ACTIVE.getValue());
                vehicle.setCreatedTime(new Date());
                vehicleUserMapper.insert(vehicle);
            }
            tokenRes = getLoginTokenRes(vehicle.getUuid(),vehicle.getUserName(),vehicle.getMobile(),UserTypeEnum.vehicle.getType());
        }else{
            //????????????/????????????????????????
            Technician technician = getTechnician(accountName);
            if(!StringUtils.isEmpty(technician)){
                tokenRes = getLoginTokenRes(technician.getUuid(),technician.getUserName(),technician.getMobile(),UserTypeEnum.technician.getType());
                tokenRes.setCheckSts(technician.getCheckSts());
            }else{
                //????????????????????????
                StoreUser storeUser = getStoreUser(accountName);
                if(StringUtils.isEmpty(storeUser)){
                    throw new BusinessException(ResEnum.LOGIN_ACCOUNT_ERROR);
                }
                //??????????????????
                Store store = getStoreDetailByUuid(storeUser.getStoreUuid());
                if(StringUtils.isEmpty(store)){
                    throw new BusinessException(ResEnum.STORE_INFO_NOT_BY_USER);
                }
                tokenRes = getLoginTokenRes(storeUser.getUuid(),storeUser.getUserName(),storeUser.getMobile(),UserTypeEnum.store.getType());
                tokenRes.setCheckSts(store.getCheckSts());
            }
        }
        
        log.info("?????????????????????token???{}",tokenRes.getToken());
        return ResultRes.success(tokenRes);
    }

    /**
     * ????????????????????????????????????
     * @param accountName
     * @return
     */
    private StoreUser getStoreUser(String accountName){
        StoreUser store = new StoreUser();
        store.setMobile(accountName);
        store.setSts(StsEnum.ACTIVE.getValue());
        store = storeUserMapper.selectOne(store);
        return store;
    }


    /**
     * ????????????????????????????????????
     * @param accountName
     * @return
     */
    private Technician getTechnician(String accountName){
        Technician technician = new Technician();
        technician.setMobile(accountName);
        technician.setSts(StsEnum.ACTIVE.getValue());
        technician = technicianMapper.selectOne(technician);
        return technician;
    }

    /**
     * ????????????????????????????????????
     * @param accountName
     * @return
     */
    private VehicleUser getVehicleUser(String accountName){
        VehicleUser vehicle = new VehicleUser();
        vehicle.setMobile(accountName);
        vehicle.setSts(StsEnum.ACTIVE.getValue());
        vehicle = vehicleUserMapper.selectOne(vehicle);
        return vehicle;
    }

    @Override
    public ResultRes<LoginRes> switchRole() {
        Integer userType = TokenHelper.getLoginToken().getUserType();
        String mobile = TokenHelper.getLoginToken().getUserMobile();
        if(UserTypeEnum.store.getType().equals(userType)){
            //???????????? ?????????????????????????????????
            Technician technician = getTechnician(mobile);
            if(StringUtils.isEmpty(technician)){
                throw new BusinessException(ResEnum.TECHNICIAN_NOT_EXIST);
            }
            LoginRes tokenRes  = getLoginTokenRes(technician.getUuid(),technician.getUserName(),TokenHelper.getLoginToken().getUserMobile(),UserTypeEnum.technician.getType());
            tokenRes.setCheckSts(technician.getCheckSts());
            return ResultRes.success(tokenRes);
        }else if(UserTypeEnum.technician.getType().equals(userType)){
            //????????????
            StoreUser storeUser = getStoreUser(mobile);
            if(StringUtils.isEmpty(storeUser)){
                throw new BusinessException(ResEnum.STORE_CONTACT_NOT_EXIST);
            }
            //??????????????????
            Store store = getStoreDetailByUuid(storeUser.getStoreUuid());
            if(StringUtils.isEmpty(store)){
                throw new BusinessException(ResEnum.STORE_INFO_NOT_BY_USER);
            }

            LoginRes tokenRes  = getLoginTokenRes(storeUser.getUuid(),storeUser.getUserName(),TokenHelper.getLoginToken().getUserMobile(),UserTypeEnum.store.getType());
            tokenRes.setCheckSts(store.getCheckSts());
            return ResultRes.success(tokenRes);
        }else {

            log.error("?????????????????????????????????,userType:{}",userType);
            throw new BusinessException(ResEnum.VEHICLE_NOT_SUPPORT_SWITCH);
        }
    }

    /**
     * ??????????????????
     * @return
     */
    @Override
    public ResultRes exitLogin() {
        ResultRes res = exitLoginByUserId(TokenHelper.getUserUuid());
        return res;
    }

    /**
     * ????????????ID??????????????????
     * @param userList
     * @return
     */
    @Override
    public ResultRes exitLoginByUserId(List<String> userList) {
        if(!CollectionUtils.isEmpty(userList)){
            userList.stream().forEach(item ->{
                exitLoginByUserId(item);
            });
        }
        return ResultRes.success();
    }

    /**
     * ????????????uuid????????????
     * @param userUuid
     * @return
     */
    @Override
    public ResultRes exitLoginByUserUuid(String userUuid) {
        return exitLoginByUserId(userUuid);
    }

    /**
     * ????????????ID??????????????????
     * @param uuid
     * @return
     */
    public Store getStoreDetailByUuid(String uuid) {
        Store storeDetail = new Store();
        storeDetail.setUuid(uuid);
        storeDetail.setSts(StsEnum.ACTIVE.getValue());
        storeDetail = storeMapper.selectOne(storeDetail);
        return storeDetail;
    }

    /**
     * ????????????ID????????????
     * @param userUuid
     * @return
     */
    private ResultRes exitLoginByUserId(String userUuid){
        LoginToken token = new LoginToken();
        token.setUuid(userUuid);
        // ???token ??? redis????????????????????????
        long remainingTime = redisUtils.getExpireTime(token.cacheKey(), TimeUnit.SECONDS);
        // redis???????????????????????????
        String logoutKey = token.logoutCacheKey();
        //??????????????????
        redisUtils.remove(token.cacheKey());
        boolean b = redisUtils.set(logoutKey, "", remainingTime, TimeUnit.SECONDS);
        if (true){
            return ResultRes.success();
        }else {
            return ResultRes.error(ResEnum.EXIT_ERROR.getValue(),ResEnum.EXIT_ERROR.getDesc());
        }
    }

    private LoginRes getLoginTokenRes(String personUuid,String personName,String personMobile,Integer personType){
        return getLoginTokenRes(personUuid,personName,personType,personMobile,null);
    }

    /**
     * ??????????????????????????????
     * @param personUuid
     * @param personName
     * @param personType
     * @param loginChannel
     * @return
     */
    private LoginRes getLoginTokenRes(String personUuid,String personName,Integer personType,String personMobile,String loginChannel){
        LoginToken loginToken = new LoginToken();
        loginToken.setUuid(personUuid);
        loginToken.setUserUuid(personUuid);
        loginToken.setUserName(StringUtils.isEmpty(personName) ? personUuid : personName);
        loginToken.setLoginTime(new Date());
        loginToken.setUserMobile(personMobile);
        loginToken.setUserType(personType);
        // ??????token
        String token = TokenUtil.createToken(loginToken);
        // ????????????token??????redis
        redisUtils.set(loginToken.cacheKey(), token, (long)loginToken.getExpireTime(), TimeUnit.MINUTES);
        //??????????????????
        LoginRes tokenRes = new LoginRes();
        tokenRes.setToken(token);
        tokenRes.setExpires_in(loginToken.getExpireTime());
        tokenRes.setUserType(personType);
        tokenRes.setUuid(personUuid);
        return tokenRes;
    }


    /**
     * ??????????????????
     * @param param
     */
    private void validUserLogin(LoginReq param){
        //???????????????
        String cacheKey = String.format(Constants.LOGIN_VERIFICATION_CODE_CACHE_KEY, new Object[] { param.getAccountName() });
        String redisCode = String.valueOf(redisUtils.get(cacheKey));
        if(StringUtils.isEmpty(redisCode)){
            throw new BusinessException(ResEnum.VERIFICATION_CODE_ERROR.getValue(),ResEnum.VERIFICATION_CODE_ERROR.getDesc());
        }
        //?????????1????????? ???????????? 6 ???
        Long validExpireTime = 60L;
        String validCodeKey = String.format(Constants.VERIFICATION_CODE_TIMES_CACHE_KEY,param.getAccountName(),redisCode);
        log.info(">>>>>>>>>>>>>>validCodeKey:{}",validCodeKey);
        Object validCodeKeyNum = redisUtils.get(validCodeKey);
        if(null == validCodeKeyNum){
            final String maxNum = "6";
            redisUtils.set(validCodeKey,maxNum,validExpireTime,TimeUnit.SECONDS);
        }else{
            Integer leftNum = Integer.valueOf(validCodeKeyNum.toString());
            if(leftNum < 1){
                throw new BusinessException(ResEnum.OPERATION_FREQUENTLY_ERROR);
            }else{
                redisUtils.set(validCodeKey,String.valueOf((leftNum-1)),validExpireTime,TimeUnit.SECONDS);
            }
        }
        //???????????????????????????
        if(!redisCode.equals(param.getCode())){
            throw new BusinessException(ResEnum.VERIFICATION_CODE_ERROR.getValue(),ResEnum.VERIFICATION_CODE_ERROR.getDesc());
        }
        //???????????????
        redisUtils.remove(cacheKey);

    }

    /**
     * ??????????????????????????????????????????
     * @param request
     * @param methodName
     */
    public void checkSendRate(HttpServletRequest request,String methodName){
        String userIp = IpUtils.getRequestIp(request);
        String cacheKey = String.format(Constants.contents, methodName,userIp);
        Object o = redisUtils.get(cacheKey);
        String cacheCount = (null != o) ? (String) o : "";
        if(StringUtils.isEmpty(cacheCount)){
            //???????????????????????????????????????????????????,??????1??????
            redisUtils.set(cacheKey,String.valueOf(1),1L, TimeUnit.MINUTES);
            log.info("----------------"+String.valueOf(o));
        }else if(Integer.valueOf(cacheCount) >= Constants.OPERATING_FREQUENCY){
            //????????????????????????????????????
            throw new BusinessException(ResEnum.OPERATION_FREQUENTLY_ERROR);
        }else{
            redisUtils.set(cacheKey,String.valueOf(Integer.valueOf(cacheCount) + 1),1L, TimeUnit.MINUTES);
        }
    }
}
