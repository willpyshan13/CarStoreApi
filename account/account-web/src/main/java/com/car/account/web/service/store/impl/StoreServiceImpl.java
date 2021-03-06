package com.car.account.web.service.store.impl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import com.car.account.client.request.store.*;
import com.car.account.web.model.store.*;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.car.account.client.enums.store.StoreImagesTypeEnum;
import com.car.account.client.enums.store.StoreUserTypeEnum;
import com.car.account.client.request.goods.QueryGoodsClassifyReq;
import com.car.account.client.response.comment.CommentStaticsRes;
import com.car.account.client.response.goods.GoodsRes;
import com.car.account.client.response.goods.ext.StoreGoodsClassifyRes;
import com.car.account.client.response.goods.ext.sub.ClassifyRes;
import com.car.account.client.response.goods.sub.GoodsDetailRes;
import com.car.account.client.response.goods.sub.GoodsImgRes;
import com.car.account.client.response.store.QueryShareStoreListRes;
import com.car.account.client.response.store.QueryStoreListRes;
import com.car.account.client.response.store.StoreAccountRes;
import com.car.account.client.response.store.StoreCommentStaticsRes;
import com.car.account.client.response.store.StoreDetailRes;
import com.car.account.client.response.store.StoreUserRes;
import com.car.account.client.response.store.sub.ImgStoreRes;
import com.car.account.web.common.constants.StoreConstants;
import com.car.account.web.mapper.goods.GoodsDetailMapper;
import com.car.account.web.mapper.goods.GoodsImagesMapper;
import com.car.account.web.mapper.goods.GoodsMapper;
import com.car.account.web.mapper.goods.GoodsParentMapper;
import com.car.account.web.mapper.store.StoreAccountMapper;
import com.car.account.web.mapper.store.StoreBrandMapper;
import com.car.account.web.mapper.store.StoreImagesMapper;
import com.car.account.web.mapper.store.StoreMapper;
import com.car.account.web.mapper.store.StoreUserMapper;
import com.car.account.web.model.goods.Goods;
import com.car.account.web.model.goods.GoodsDetail;
import com.car.account.web.model.goods.GoodsImages;
import com.car.account.web.model.goods.GoodsParent;
import com.car.account.web.service.person.PersonService;
import com.car.account.web.service.store.StoreService;
import com.car.account.web.service.vehicle.VehicleConfigService;
import com.car.common.enums.CheckStatusEnum;
import com.car.common.enums.ResEnum;
import com.car.common.enums.StsEnum;
import com.car.common.enums.UserTypeEnum;
import com.car.common.exception.BusinessException;
import com.car.common.res.PageRes;
import com.car.common.res.ResultRes;
import com.car.common.utils.Constants;
import com.car.common.utils.DateUtil;
import com.car.common.utils.ExcelUtils;
import com.car.common.utils.ExceptionUtils;
import com.car.common.utils.RedisUtils;
import com.car.common.utils.StringUtil;
import com.car.common.utils.TokenHelper;
import com.car.common.utils.UuidUtils;
import com.car.system.client.feign.SystemFeign;
import com.car.system.client.response.area.AreaRes;
import com.car.system.client.response.dict.DictionaryRes;
import com.car.utility.client.feign.BaiduFeign;
import com.car.utility.client.feign.SmsFeign;
import com.car.utility.client.response.BaiduLatitudeLongitudeLocationRes;
import com.car.utility.client.response.BaiduLatitudeLongitudeRes;
import com.car.utility.client.response.LocationResultRes;
import com.codingapi.txlcn.tc.annotation.TxcTransaction;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * @author zhouz
 * @date 2020/12/19
 */
@Slf4j
@Service
public class StoreServiceImpl implements StoreService {

	@Autowired
	StoreMapper storeMapper;
	@Autowired
	StoreImagesMapper storeImagesMapper;
	@Autowired
	StoreUserMapper storeUserMapper;
	@Autowired
	StoreAccountMapper storeAccountMapper;
	@Autowired
	GoodsMapper goodsMapper;
	@Autowired
	GoodsParentMapper goodsParentMapper;
	@Autowired
	StoreBrandMapper storeBrandMapper;

	@Autowired
	GoodsImagesMapper goodsImagesMapper;
	@Autowired
	GoodsDetailMapper goodsDetailMapper;

	@Resource
	SmsFeign smsFeign;
	@Autowired
	RedisUtils redisUtils;

	@Autowired
	PersonService personService;
	@Autowired
	VehicleConfigService vehicleConfigService;
	@Autowired
	SystemFeign systemFeign;
	@Autowired
	BaiduFeign baiduFeign;

	/**
	 * ????????????
	 * @param addStoreReq
	 * @return
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	@TxcTransaction
	public ResultRes<String> addStore(AddStoreReq addStoreReq) {
		log.debug("?????? ??????????????????????????????");
		Store queryStore = new Store();
		queryStore.setStoreName(addStoreReq.getStoreName());
		queryStore.setSts(StsEnum.ACTIVE.getValue());
		queryStore = storeMapper.selectOne(queryStore);
		if (queryStore != null) {
			log.error("????????????????????????{}", addStoreReq.getStoreName());
			throw new BusinessException(ResEnum.STORE_NAME_EXIST);
		}
		log.debug("?????? ????????????????????????????????????");
		List<StoreUserReq> contactsList = addStoreReq.getStoreUserReq();
		if (CollectionUtils.isEmpty(contactsList)) {
			log.error("?????? ??????????????????null");
			throw new BusinessException(ResEnum.NOT_STORE_CONTACT);
		}

		List<String> brandUuidList = addStoreReq.getBrandUuidList();
		if (CollectionUtils.isEmpty(brandUuidList)) {
			log.error("?????? ?????????????????????null");
			throw new BusinessException(ResEnum.NOT_STORE_BRAND);
		}

		// ??????????????????????????????????????? TODO ?????????
		contactsList.stream().forEach(s -> {
			checkMobileAlreadyExist(s.getMobile(), null);
		});

		// ???????????????????????? TODO ?????????
		checkBrandAlreadyExist(brandUuidList);

		String userName = TokenHelper.getUserName();
		String storeUuid = UuidUtils.getUuid();
		// ????????????
		Store addStore = new Store();
		addStore.setUuid(storeUuid);
		addStore.setSts(StsEnum.ACTIVE.getValue());
		addStore.setCreatedTime(new Date());
		addStore.setCreatedBy(userName);
		BeanUtils.copyProperties(addStoreReq, addStore);
		addStore.setCheckSts(CheckStatusEnum.CHECK_PENDING.getValue());

		// ???????????????????????????????????????????????????
		setStorePosition(addStore);
		storeMapper.insert(addStore);

		// ???????????????????????????
		batchInsertStoreUser(addStore.getUuid(), contactsList);

		insertOrUpdateBrand(false, storeUuid, brandUuidList);

		// ??????????????????
		StoreAccountReq storeAccountReq = addStoreReq.getStoreAccountReq();
		if (!StringUtils.isEmpty(storeAccountReq)) {
			insertStoreAccount(storeAccountReq, storeUuid);
		}

		// ????????????????????????
		List<String> businessImgList = addStoreReq.getBusinessImgList();
		List<String> otherImgList = addStoreReq.getOtherImgList();
		List<String> shopImgList = addStoreReq.getShopImgList();
		batchAddStoreImages(false, storeUuid, businessImgList, shopImgList, otherImgList);
		return ResultRes.success(storeUuid);
	}

	/**
	 * ???????????????????????????
	 * @param store
	 */
	private void setStorePosition(Store store) {

		String addressDetail = getSystemAreaName(store.getCompanyAddressProvince())
				+ getSystemAreaName(store.getCompanyAddressCity()) + store.getCompanyAddressDetail();
		ResultRes<LocationResultRes> locationResult = baiduFeign.getAddressLatitudeLongitude(addressDetail);
		if (locationResult.isSuccess()) {
			LocationResultRes location = locationResult.getData();
			if (!StringUtils.isEmpty(location)) {
				BaiduLatitudeLongitudeRes baiduLatitudeLongitudeRes = location.getResult();
				if (!StringUtils.isEmpty(baiduLatitudeLongitudeRes)) {
					BaiduLatitudeLongitudeLocationRes baiduLatitudeLongitudeLocationRes = baiduLatitudeLongitudeRes
							.getLocation();
					if (!StringUtils.isEmpty(baiduLatitudeLongitudeLocationRes)) {
						store.setLatitude(baiduLatitudeLongitudeLocationRes.getLat());
						store.setLongitude(baiduLatitudeLongitudeLocationRes.getLng());
					}
				}
			}
		} else {
			throw new BusinessException(ResEnum.POSITION_ERROR);
		}
	}

	/**
	 * ??????????????????
	 * @param areaUuid
	 * @return
	 */
	private String getSystemAreaName(String areaUuid) {
		ResultRes<AreaRes> areaRes = systemFeign.queryArea(areaUuid);
		if (areaRes.isSuccess()) {
			return areaRes.getData().getAreaName();
		}
		return null;
	}

	/**
	 * ????????????
	 * @param storeUuid
	 * @return
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	@TxcTransaction
	public ResultRes<String> deleteStore(String storeUuid) {
		log.debug("??????  ??????????????????????????? uuid{}", storeUuid);
		Store deleteStore = checkStoreAlreadyExist(storeUuid);
		deleteStore.setUuid(storeUuid);
		deleteStore.setSts(StsEnum.INVALID.getValue());
		storeMapper.updateByPrimaryKeySelective(deleteStore);

		storeAccountMapper.deleteStoreAccountByStoreUuid(storeUuid);
		storeUserMapper.deleteStoreUser(storeUuid);
		storeImagesMapper.deleteStoreImagesByStoreUuid(storeUuid);

		return ResultRes.success(storeUuid);
	}

	/**
	 * ????????????
	 * @param updateStoreReq
	 * @return
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	@TxcTransaction
	public ResultRes<String> updateStore(UpdateStoreReq updateStoreReq) {
		if (!StringUtils.isEmpty(updateStoreReq.getStoreName())) {
			log.debug("??????  ?????????????????????????????? ???????????? {}", updateStoreReq.getStoreName());
			Store store = getStoreByName(updateStoreReq.getStoreName());
			if (!StringUtils.isEmpty(store) && !store.getUuid().equals(updateStoreReq.getUuid())) {
				log.error("?????? ????????????????????????{}", updateStoreReq.getStoreName());
				throw new BusinessException(ResEnum.STORE_NAME_EXIST);
			}
		}

		// ??????????????????????????????????????????????????????????????????????????????token
		if (!(CheckStatusEnum.APPROVE.getValue().equals(updateStoreReq.getCheckSts()))) {
			// ??????????????????????????????uuid
			List<String> storeUserUuidList = storeUserMapper.queryStoreUserUuid(updateStoreReq.getUuid());
			personService.exitLoginByUserId(storeUserUuidList);
		}

		log.debug("??????  ???????????????????????? uuid {}", updateStoreReq.getUuid());
		Store store = checkStoreAlreadyExist(updateStoreReq.getUuid());
		List<String> brandUuidList = updateStoreReq.getBrandUuidList();
		if (!CollectionUtils.isEmpty(brandUuidList)) {
			// ???????????????????????? TODO ?????????
			checkBrandAlreadyExist(brandUuidList);
			// ????????????
			insertOrUpdateBrand(true, store.getUuid(), brandUuidList);
		}
		// ????????????????????????
		batchAddStoreImages(true, updateStoreReq.getUuid(), updateStoreReq.getBusinessImgList(),
				updateStoreReq.getShopImgList(), updateStoreReq.getOtherImgList());
		// ?????????????????????
		List<StoreUserReq> storeUserReqList = updateStoreReq.getStoreUserReqList();
		this.updateStoreUser(storeUserReqList, store);
		// ????????????????????????
		StoreAccountReq storeAccountReq = updateStoreReq.getStoreAccountReq();
		if (!StringUtils.isEmpty(storeAccountReq)) {
			updateStoreAccount(storeAccountReq, updateStoreReq.getUuid());
		}
		// ????????????????????????
		Store updateStore = new Store();
		updateStore.setLastUpdatedBy(TokenHelper.getUserName());
		updateStore.setLastUpdatedTime(new Date());
		BeanUtils.copyProperties(updateStoreReq, updateStore);

		// ???????????????????????????????????????????????????
		setStorePosition(updateStore);

		storeMapper.updateByPrimaryKeySelective(updateStore);
		log.debug("??????  ??????????????????{},???????????? {}", updateStoreReq.getCheckSts(), store.getCheckSts());
		if (store.getCheckSts() != null) {
			if (!store.getCheckSts().equals(updateStoreReq.getCheckSts())
					&& !CollectionUtils.isEmpty(storeUserReqList)) {
				sendSms(updateStoreReq, storeUserReqList);
			}
		} else {
			if (!CheckStatusEnum.CHECK_PENDING.getValue().equals(updateStoreReq.getCheckSts())) {
				sendSms(updateStoreReq, storeUserReqList);
			}
		}
		return ResultRes.success(updateStoreReq.getUuid());
	}

	/**
	 * ?????????????????????
	 * @param storeUserReqList
	 */
	private void updateStoreUser(List<StoreUserReq> storeUserReqList, Store store) {
		if (CollectionUtils.isEmpty(storeUserReqList)) {
			log.error("????????????????????????null");
			throw new BusinessException(ResEnum.NOT_STORE_CONTACT);
		}
		// ????????????????????????????????????????????????uuid
		List<String> storeUserReqUuidList = new ArrayList<>();
		// ????????????????????????????????????????????????
		List<StoreUserReq> insertStoreUserList = new ArrayList<>();
		// ????????????????????????????????????????????????
		List<StoreUserReq> updateStoreUserList = new ArrayList<>();
		storeUserReqList.forEach(storeUserReq -> {
			// ??????uuid???????????????
			if (StringUtils.isEmpty(storeUserReq.getUuid())) {
				insertStoreUserList.add(storeUserReq);
			} else {
				updateStoreUserList.add(storeUserReq);
			}
			storeUserReqUuidList.add(storeUserReq.getUuid());
		});
		// ?????????????????????????????????
		List<StoreUser> selectStoreUser = getStoreUserList(store.getUuid());
		// ?????????????????????????????????????????????uuid
		List<String> storeUserUuidList = new ArrayList<>();
		selectStoreUser.forEach(storeUser -> {
			storeUserUuidList.add(storeUser.getUuid());
		});
		// ????????????????????????????????????uuid
		List<String> deleteStoreUuidList = checkList(storeUserReqUuidList, storeUserUuidList);
		// ??????????????????????????????????????????
		if (!CollectionUtils.isEmpty(deleteStoreUuidList)) {
			batchDeleteStoreUser(deleteStoreUuidList);
		}
		// ?????????????????????????????????
		if (!CollectionUtils.isEmpty(insertStoreUserList)) {
			batchInsertStoreUser(store.getUuid(), insertStoreUserList);
		}
		// ?????????????????????????????????
		if (!CollectionUtils.isEmpty(updateStoreUserList)) {
			batchUpdateStoreUser(updateStoreUserList);
		}
	}

	/**
	 *
	 * ?????????????????????????????????
	 * @param deleteStoreUuidList
	 */
	private void batchDeleteStoreUser(List<String> deleteStoreUuidList) {
		if (!CollectionUtils.isEmpty(deleteStoreUuidList)) {
			// ?????????????????????
			int deleteStoreUserNum = storeUserMapper.batchDeleteStoreUser(deleteStoreUuidList);
			if (deleteStoreUserNum <= 0) {
				log.error("?????????????????????????????????");
				throw new BusinessException(ResEnum.DELETE_DB_ERROR);
			}
			// ??????uuid????????????????????????
			personService.exitLoginByUserId(deleteStoreUuidList);
		}
	}

	/**
	 * ?????????????????????????????????
	 * @param storeUserReqList
	 */
	private void batchUpdateStoreUser(List<StoreUserReq> storeUserReqList) {
		// ???????????????????????????
		String userName = TokenHelper.getUserName();
		// ???????????????
		int adminUserCount = 0;
		for (StoreUserReq storeUserReq : storeUserReqList) {
			// ???????????????????????????????????????
			checkMobileAlreadyExist(storeUserReq.getMobile(), storeUserReq.getUuid());
			if (StoreUserTypeEnum.ADMIN.getValue().equals(storeUserReq.getPersonType())) {
				adminUserCount++;
				if (adminUserCount > 1) {
					log.error("??????  ?????????????????????????????????????????????");
					throw new BusinessException(ResEnum.STORE_USER_ADMIN_NOT_MORE);
				}
			}
			int updateStoreUserNum = storeUserMapper.batchUpdateStoreUser(storeUserReq, userName);
			if (updateStoreUserNum <= 0) {
				log.error("???????????????????????????????????????");
				throw new BusinessException(ResEnum.UPDATE_DB_ERROR);
			}
		}

	}

	/**
	 * ??????listTwo??????listone???????????????
	 * @return
	 */
	private List<String> checkList(List<String> listOne, List<String> listTwo) {
		// diff ?????????????????????
		List<String> diff = new ArrayList<>();
		for (String str : listTwo) {
			if (!listOne.contains(str)) {
				diff.add(str);
			}
		}
		return diff;
	}

	/**
	 * ????????????????????????????????????
	 * @param storeName
	 * @return
	 */
	private Store getStoreByName(String storeName) {
		Store queryStoreName = new Store();
		queryStoreName.setStoreName(storeName);
		queryStoreName.setSts(StsEnum.ACTIVE.getValue());
		List<Store> selectList = storeMapper.select(queryStoreName);
		if (!CollectionUtils.isEmpty(selectList)) {
			return selectList.get(0);
		}
		return null;
	}

	/**
	 * ????????????ID???????????????????????????
	 * @param storeUuid
	 * @return
	 */
	private List<StoreUser> getStoreUserList(String storeUuid) {
		StoreUser search = new StoreUser();
		search.setStoreUuid(storeUuid);
		List<StoreUser> storeUserList = storeUserMapper.select(search);
		return storeUserList;
	}

	@Override
	public ResultRes<String> updateStoreAccount(StoreAccountReq storeAccountReq) {
		String userUuid = TokenHelper.getUserUuid();
		StoreUser storeUser = storeUserMapper.selectByPrimaryKey(userUuid);
		if (StringUtils.isEmpty(storeUser)) {
			log.error("????????????????????????  ????????????????????????????????????userUuid{}", userUuid);
			throw new BusinessException(ResEnum.NON_EXISTENT);
		}

		String storeUuid = storeUser.getStoreUuid();
		log.debug("????????????????????????  ???????????????????????? storeUuid {}", storeUuid);
		checkStoreAlreadyExist(storeUuid);
		updateStoreAccount(storeAccountReq, storeUuid);
		return ResultRes.success(storeUuid);
	}

	/**
	 * ??????????????????
	 * @param param
	 * @return
	 */
	@Override
	public PageRes<List<QueryStoreListRes>> queryStoreList(QueryStoreListReq param) {
		log.debug("??????????????????");
		// ??????????????????????????????
		Integer userType = TokenHelper.getUserType();
		// ????????????
		Integer checkSts = null;
		if (UserTypeEnum.vehicle.getType().equals(userType)) {
			checkSts = CheckStatusEnum.APPROVE.getValue();
		}
		PageHelper.startPage(param.getPageNum(), param.getPageSize());
		List<QueryStoreListRes> storeList = storeMapper.queryStoreList(param, checkSts);
		if (!CollectionUtils.isEmpty(storeList)) {
			storeList.stream().forEach(s -> {

				// ??????????????????????????????
				String storeUuid = s.getUuid();
				StoreImages sm = new StoreImages();
				sm.setSts(StsEnum.ACTIVE.getValue());
				sm.setStoreUuid(storeUuid);
				List<StoreImages> smList = storeImagesMapper.select(sm);
				if (!CollectionUtils.isEmpty(smList)) {

					List<ImgStoreRes> igList = new ArrayList<>();
					smList.stream().forEach(ss -> {

						ImgStoreRes is = new ImgStoreRes();
						BeanUtils.copyProperties(ss, is);
						igList.add(is);
					});

					// ????????????2 ????????????
					List<ImgStoreRes> collect = igList.stream()
							.sorted(Comparator.comparingInt(ImgStoreRes::getImageType).reversed())
							.collect(Collectors.toList());
					s.setImgList(collect);
				}
				// ????????????????????????
				List<String> classifyList = queryStoreServices(storeUuid);
				s.setClassifyList(classifyList);

				List<String> configNameList = vehicleConfigService
						.queryListByUuid(storeBrandMapper.queryBrandByStoreUuid(storeUuid)).stream()
						.map(d -> d.getConfigName()).collect(Collectors.toList());
				s.setConfigNameList(configNameList);

//                //?????????????????????????????????
//                CommentStaticsRes statics = queryStoreCommentStatics(storeUuid);
//                if(null != statics){
//
//                    s.setCommentNum(statics.getTotalNum());
//                    s.setCommentScore(statics.getScore());
//                }
			});
		}

		// TODO ?????????????????? ????????????????????????4s ???
		PageInfo<QueryStoreListRes> pageInfo = new PageInfo<>(storeList);
		return PageRes.success(storeList, pageInfo.getPageSize(), (int) pageInfo.getTotal(), pageInfo.getPages());
	}

	/**
	 * ??????????????????
	 * @param uuid
	 * @return
	 */
	@Override
	public ResultRes<StoreDetailRes> queryStoreDetail(String uuid) {
		log.debug("??????  ???????????????????????? uuid {}", uuid);
		checkStoreAlreadyExist(uuid);
		StoreDetailRes storeDetailRes = storeMapper.queryStoreDetail(uuid);
		if (!StringUtils.isEmpty(storeDetailRes)) {
			// ????????????
			StoreImages queryStoreImages = new StoreImages();
			queryStoreImages.setStoreUuid(uuid);
			queryStoreImages.setSts(StsEnum.ACTIVE.getValue());
			List<StoreImages> imgList = storeImagesMapper.select(queryStoreImages);
			if (!CollectionUtils.isEmpty(imgList)) {
				List<String> businessImgList = new ArrayList<>();
				List<String> shopImgList = new ArrayList<>();
				List<String> otherImgList = new ArrayList<>();
				for (StoreImages storeImages : imgList) {
					if (StoreImagesTypeEnum.BUSINESS_IMG.getValue().equals(storeImages.getImageType())) {
						businessImgList.add(storeImages.getImageUrl());
					} else if (StoreImagesTypeEnum.SHOP_IMG.getValue().equals(storeImages.getImageType())) {
						shopImgList.add(storeImages.getImageUrl());
					} else if (StoreImagesTypeEnum.OTHER_IMG.getValue().equals(storeImages.getImageType())) {
						otherImgList.add(storeImages.getImageUrl());
					}
				}
				storeDetailRes.setBusinessImgList(businessImgList);
				storeDetailRes.setShopImgList(shopImgList);
				storeDetailRes.setOtherImgList(otherImgList);
			}
			// ????????????
			StoreUser queryStoreUser = new StoreUser();
			queryStoreUser.setStoreUuid(uuid);
			queryStoreUser.setSts(StsEnum.ACTIVE.getValue());
			List<StoreUser> storeUserList = storeUserMapper.select(queryStoreUser);
			if (!CollectionUtils.isEmpty(storeUserList)) {
				List<StoreUserRes> storeUserResList = new ArrayList<>();
				StoreUserRes storeUserRes;
				for (StoreUser storeUser : storeUserList) {
					storeUserRes = new StoreUserRes();
					BeanUtils.copyProperties(storeUser, storeUserRes);
					storeUserResList.add(storeUserRes);
				}
				storeDetailRes.setStoreUserResList(storeUserResList);
			}

			// ????????????????????????
			StoreAccount storeAccount = new StoreAccount();
			storeAccount.setStoreUuid(uuid);
			storeAccount.setSts(StsEnum.ACTIVE.getValue());
			storeAccount = storeAccountMapper.selectOne(storeAccount);
			if (!StringUtils.isEmpty(storeAccount)) {
				StoreAccountRes storeAccountRes = new StoreAccountRes();
				BeanUtils.copyProperties(storeAccount, storeAccountRes);
				storeDetailRes.setStoreAccountRes(storeAccountRes);
			}

			// ????????????
			List<String> brandUuidList = storeBrandMapper.queryBrandByStoreUuid(uuid);
			storeDetailRes.setBrandUuidList(brandUuidList);
		}

		// TODO ??????????????????
		StoreCommentStaticsRes comment = new StoreCommentStaticsRes();
		comment.setTotalNum(121);
		comment.setStoreUuid(uuid);
		comment.setScore(BigDecimal.valueOf(4.78));
		comment.setEnvironmentScore(BigDecimal.valueOf(5.0));
		comment.setTechnologyScore(BigDecimal.valueOf(4.8));
		comment.setServiceScore(BigDecimal.valueOf(4.6));
		storeDetailRes.setCommentStatics(comment);
		return ResultRes.success(storeDetailRes);
	}

	/**
	 * ??????token??????????????????
	 * @param
	 * @return
	 */
	@Override
	public ResultRes<StoreDetailRes> queryStoreDetail() {
		String userUuid = TokenHelper.getUserUuid();
		Integer userType = TokenHelper.getUserType();

		if (!UserTypeEnum.store.getType().equals(userType)) {
			log.error("???????????????????????????>>>userUuid:{},userType:{}", userUuid, userType);
			throw new BusinessException(ResEnum.STORE_INVALID_TYPE);
		}

		StoreUser storeUser = storeUserMapper.selectByPrimaryKey(userUuid);
		if (StringUtils.isEmpty(storeUser)) {
			log.error("??????token??????????????????  ????????????????????????????????????userUuid{}", userUuid);
			throw new BusinessException(ResEnum.NON_EXISTENT);
		}
		return queryStoreDetail(storeUser.getStoreUuid());
	}

	/**
	 * ??????????????????
	 * @param exportReq
	 * @param response
	 */
	@Override
	public void exportStoreList(QueryStoreListReq exportReq, HttpServletResponse response) {
		log.debug("??????????????????");
		try {
			List<QueryStoreListRes> storeList = storeMapper.queryStoreList(exportReq, null);
			// ????????????
//            List<QueryStoreListRes> exportStoreList = convertToRes(storeList);
			// ??????????????????
			InputStream resourceAsStream = getClass().getClassLoader()
					.getResourceAsStream(StoreConstants.STORE_INFO_EXPORT_TEMPLATE);
			// ????????????????????????
			List<QueryStoreListRes> excelList = ExcelUtils.setFieldValue(storeList);
			Workbook wb = new XSSFWorkbook(resourceAsStream);
			Sheet sheet = wb.getSheetAt(0);
			// ????????????????????????
			int firstRowIndex = sheet.getFirstRowNum() + 2;
			for (int rowIndex = firstRowIndex; rowIndex < excelList.size() + 2; rowIndex++) {
				// ?????????
				Row rowStyle = (rowIndex % 2) == 0 ? sheet.getRow(2) : sheet.getRow(3);
				// ????????????
				CellStyle cellStyle = ExcelUtils.getExcelFormat(rowStyle.getCell(1));
				CellStyle cellStyle1 = ExcelUtils.getExcelFormat(rowStyle.getCell(0));
				Row row = sheet.getRow(rowIndex);
				if (row == null) {
					row = sheet.createRow(rowIndex);
				}
				row.setHeight(rowStyle.getHeight());
				QueryStoreListRes exportDto = excelList.get(rowIndex - 2);
				ExcelUtils.setCell(row, cellStyle1, 0, rowIndex - 1);
				ExcelUtils.setCell(row, cellStyle, 1, exportDto.getStoreName());
				ExcelUtils.setCell(row, cellStyle, 2, exportDto.getStoreTypeName());
				ExcelUtils.setCell(row, cellStyle, 3,
						exportDto.getAddressProvinceName() + "-" + exportDto.getAddressCityName());

				ExcelUtils.setCell(row, cellStyle, 4, exportDto.getUserName());
				ExcelUtils.setCell(row, cellStyle, 5, exportDto.getMobile());
				ExcelUtils.setCell(row, cellStyle, 6, CheckStatusEnum.enumOfDesc(exportDto.getCheckSts()));
				ExcelUtils.setCell(row, cellStyle, 7,
						DateUtil.dateToStr(exportDto.getCreatedTime(), DateUtil.YYYY_MM_DD_HH_MM_SS));

			}
			ExcelUtils.responseWrite(wb, response, StoreConstants.STORE_INFO_EXPORT_TEMPLATE);
		} catch (Exception ex) {
			log.error("??????????????????????????????????????????{}", ExceptionUtils.stackTraceToString(ex));
		}

	}

	/**
	 * ????????????????????????
	 * @param brandUuidList
	 */
	private void checkBrandAlreadyExist(List<String> brandUuidList) {
		// TODO
	}

	/**
	 * ??????????????????
	 * @param storeAccountReq
	 * @param storeUuid
	 */
	private void insertStoreAccount(StoreAccountReq storeAccountReq, String storeUuid) {
		StoreAccount storeAccount = new StoreAccount();
		storeAccount.setUuid(UuidUtils.getUuid());
		storeAccount.setStoreUuid(storeUuid);
		storeAccount.setSts(StsEnum.ACTIVE.getValue());
		storeAccount.setCreatedTime(new Date());
		storeAccount.setCreatedBy(TokenHelper.getUserName());
		BeanUtils.copyProperties(storeAccountReq, storeAccount);
		storeAccountMapper.insert(storeAccount);
	}

	/**
	 * ????????????????????????
	 * @param storeAccountReq
	 * @param storeUuid
	 */
	private void updateStoreAccount(StoreAccountReq storeAccountReq, String storeUuid) {
		StoreAccount storeAccount = new StoreAccount();
		storeAccount.setStoreUuid(storeUuid);
		storeAccount.setSts(StsEnum.ACTIVE.getValue());
		storeAccount = storeAccountMapper.selectOne(storeAccount);
		if (StringUtils.isEmpty(storeAccount)) {
			// ????????????????????????
			insertStoreAccount(storeAccountReq, storeUuid);
		} else {
			// ????????????????????????
			BeanUtils.copyProperties(storeAccountReq, storeAccount);
			storeAccount.setLastUpdatedBy(TokenHelper.getUserName());
			storeAccount.setLastUpdatedTime(new Date());
			storeAccountMapper.updateByPrimaryKeySelective(storeAccount);
		}
	}

	/**
	 * ?????????????????????????????????
	 * @param isUpdate
	 * @param storeUuid
	 * @param brandUuidList
	 */
	private void insertOrUpdateBrand(boolean isUpdate, String storeUuid, List<String> brandUuidList) {
		if (isUpdate) {
			StoreBrand storeBrand = new StoreBrand();
			storeBrand.setStoreUuid(storeUuid);
			storeBrandMapper.delete(storeBrand);
		}
		List<StoreBrand> storeBrandList = new ArrayList<>();
		brandUuidList.stream().forEach(brandUuid -> {
			StoreBrand storeBrand = new StoreBrand();
			storeBrand.setUuid(UuidUtils.getUuid());
			storeBrand.setCreatedBy(TokenHelper.getUserName());
			storeBrand.setCreatedTime(new Date());
			storeBrand.setStoreUuid(storeUuid);
			storeBrand.setBrandUuid(brandUuid);
			storeBrand.setSts(StsEnum.ACTIVE.getValue());
			storeBrandList.add(storeBrand);
			storeBrandMapper.insert(storeBrand);
		});
	}

	/**
	 * ???????????????????????????
	 * @param storeUuid
	 * @param storeUserReqList
	 */
	private void batchInsertStoreUser(String storeUuid, List<StoreUserReq> storeUserReqList) {
		List<StoreUser> storeUserList = new ArrayList<>();
		StoreUser storeUser;
		// ???????????????
		int adminUserCount = 0;
		for (StoreUserReq storeUserReq : storeUserReqList) {
			// ???????????????????????????????????????
			checkMobileAlreadyExist(storeUserReq.getMobile(), null);

			if (StoreUserTypeEnum.ADMIN.getValue().equals(storeUserReq.getPersonType())) {
				adminUserCount++;
				if (adminUserCount > 1) {
					log.error("??????  ?????????????????????????????????????????????");
					throw new BusinessException(ResEnum.STORE_USER_ADMIN_NOT_MORE);
				}
			}
			storeUser = new StoreUser();
			BeanUtils.copyProperties(storeUserReq, storeUser);
			storeUser.setSts(StsEnum.ACTIVE.getValue());
			storeUser.setCreatedBy(TokenHelper.getUserName());
			storeUser.setCreatedTime(new Date());
			storeUser.setUuid(UuidUtils.getUuid());
			storeUser.setStoreUuid(storeUuid);
			storeUserList.add(storeUser);
		}
		storeUserMapper.batchInsertStoreUser(storeUserList);
	}

	/**
	 * ??????????????????????????????
	 * @param isUpdate
	 * @param storeUuid
	 * @param businessImgList  ??????????????????
	 * @param shopImgList ????????????
	 * @param otherImgList ????????????
	 */
	private void batchAddStoreImages(boolean isUpdate, String storeUuid, List<String> businessImgList,
			List<String> shopImgList, List<String> otherImgList) {
		log.debug("??????????????????????????????");
		List<StoreImages> storeImagesList = new ArrayList<>();
		if (!CollectionUtils.isEmpty(businessImgList)) {
			if (isUpdate) {
				deleteStoreImages(storeUuid, StoreImagesTypeEnum.BUSINESS_IMG.getValue());
			}
			List<StoreImages> businessStoreImagesList = buildBatchStoreImagesData(storeUuid,
					StoreImagesTypeEnum.BUSINESS_IMG.getValue(), businessImgList);
			storeImagesList.addAll(businessStoreImagesList);
		}

		if (!CollectionUtils.isEmpty(shopImgList)) {
			if (isUpdate) {
				deleteStoreImages(storeUuid, StoreImagesTypeEnum.SHOP_IMG.getValue());
			}
			List<StoreImages> shopImgStoreImagesList = buildBatchStoreImagesData(storeUuid,
					StoreImagesTypeEnum.SHOP_IMG.getValue(), shopImgList);
			storeImagesList.addAll(shopImgStoreImagesList);
		}

		if (!CollectionUtils.isEmpty(otherImgList)) {
			if (isUpdate) {
				deleteStoreImages(storeUuid, StoreImagesTypeEnum.OTHER_IMG.getValue());
			}
			List<StoreImages> otherImgStoreImagesList = buildBatchStoreImagesData(storeUuid,
					StoreImagesTypeEnum.OTHER_IMG.getValue(), otherImgList);
			storeImagesList.addAll(otherImgStoreImagesList);
		}

		if (!CollectionUtils.isEmpty(storeImagesList)) {
			storeImagesMapper.batchInsertStoreImages(storeImagesList);
		}
	}

	/**
	 * ????????????????????????
	 * @param storeUuid
	 * @param imgType
	 */
	private void deleteStoreImages(String storeUuid, int imgType) {
		log.debug("????????????????????????:??????uuid{},????????????{}", storeUuid, StoreImagesTypeEnum.enumOfDesc(imgType));
		storeImagesMapper.deleteStoreImagesByParam(storeUuid, imgType);
	}

	/**
	 * ??????????????????
	 * @param storeUuid
	 * @param imgType
	 * @param imgList
	 * @return
	 */
	private List<StoreImages> buildBatchStoreImagesData(String storeUuid, int imgType, List<String> imgList) {
		List<StoreImages> storeImagesList = new ArrayList<>();
		StoreImages storeImages = null;
		for (String businessImgUrl : imgList) {
			storeImages = new StoreImages();
			storeImages.setUuid(UuidUtils.getUuid());
			storeImages.setStoreUuid(storeUuid);
			storeImages.setImageType(imgType);
			storeImages.setImageUrl(businessImgUrl);
			storeImages.setSts(StsEnum.ACTIVE.getValue());
			storeImages.setCreatedBy(TokenHelper.getUserName());
			storeImages.setCreatedTime(new Date());
			storeImagesList.add(storeImages);
		}
		return storeImagesList;
	}

	/**
	 * ????????????????????????
	 * @param updateStoreReq
	 * @param storeUserReqList
	 */
	private void sendSms(UpdateStoreReq updateStoreReq, List<StoreUserReq> storeUserReqList) {
		if (CollectionUtils.isEmpty(storeUserReqList)) {
			log.error("?????????????????????,??????????????????");
			return;
		}
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i < storeUserReqList.size(); i++) {
			stringBuilder.append(storeUserReqList.get(i).getMobile());
			if (i < storeUserReqList.size() - 1) {
				stringBuilder.append(",");
			}
		}

		try {
			if (CheckStatusEnum.APPROVE.getValue().equals(updateStoreReq.getCheckSts())) {
				smsFeign.sendStoreCheckSuccess(stringBuilder.toString(), updateStoreReq.getStoreName());
			} else if (CheckStatusEnum.CHECK_REJECTED.getValue().equals(updateStoreReq.getCheckSts())) {
				smsFeign.sendStoreCheckReject(stringBuilder.toString(), updateStoreReq.getStoreName(),
						updateStoreReq.getRejectDetail());
			}
		} catch (Exception e) {
			log.error(e.getLocalizedMessage());
		}
	}

	/**
	 * ???????????????????????????????????????
	 * @param mobile
	 */
	private void checkMobileAlreadyExist(String mobile, String storeUserUuid) {
		StoreUser queryStoreUser = new StoreUser();
		queryStoreUser.setSts(StsEnum.ACTIVE.getValue());
		queryStoreUser.setMobile(mobile);
		StoreUser storeUser = storeUserMapper.selectOne(queryStoreUser);
		if (storeUser != null) {
			if (!(storeUser.getUuid().equals(storeUserUuid))) {
				log.error("????????????????????????????????????????????????{}", mobile);
				throw new BusinessException(ResEnum.MOBILE_BINDING_STORE);
			}
		}
	}

	/**
	 * ????????????????????????
	 * @param uuid
	 */
	private Store checkStoreAlreadyExist(String uuid) {
		Store queryStore = new Store();
		queryStore.setUuid(uuid);
		queryStore = storeMapper.selectByPrimaryKey(queryStore);
		if (queryStore == null || StsEnum.INVALID.getValue().equals(queryStore.getSts())) {
			log.error("??????  ?????????????????????????????????uuid{}", uuid);
			throw new BusinessException(ResEnum.NON_EXISTENT);
		}
		return queryStore;
	}

	/**
	 * ?????????????????????
	 * @param storeList
	 * @return
	 */
	private List<QueryStoreListRes> convertToRes(List<QueryStoreListRes> storeList) {
		if (!CollectionUtils.isEmpty(storeList)) {
			// ????????????????????????????????????,???????????????????????????","??????
			Map<String, QueryStoreListRes> map = new HashMap<>(16);
			StringBuilder nameBuilder;
			StringBuilder mobileBuilder;
			for (QueryStoreListRes queryStoreListRes : storeList) {
				QueryStoreListRes storeListRes = map.get(queryStoreListRes.getUuid());
				if (storeListRes == null) {
					map.put(queryStoreListRes.getUuid(), queryStoreListRes);
				} else {
					nameBuilder = new StringBuilder();
					mobileBuilder = new StringBuilder();
					nameBuilder.append(storeListRes.getUserName()).append(",").append(queryStoreListRes.getUserName());

					mobileBuilder.append(storeListRes.getMobile()).append(",").append(queryStoreListRes.getMobile());
					storeListRes.setUserName(nameBuilder.toString());
					storeListRes.setMobile(mobileBuilder.toString());
				}
			}
			storeList.clear();
			storeList.addAll(new ArrayList<>(map.values()));
		}
		return storeList;
	}

	@Override
	public List<String> queryStoreServices(String storeUuid) {

		QueryGoodsClassifyReq params = new QueryGoodsClassifyReq();
		params.setStoreUuid(storeUuid);
		List<Goods> goodsList = goodsMapper.queryGoodsClassify(params);
		if (!CollectionUtils.isEmpty(goodsList)) {

			List<String> classifyList = new ArrayList();
			goodsList.stream().forEach(g -> {
				String subType = g.getSubType();

				String val = locationClassifyNameByCode(subType);

				if (StringUtil.isNotBlank(val)) {
					classifyList.add(val);
				}
			});
			return classifyList;
		}
		return null;
	}

	private String locationClassifyNameByCode(String classifyCode) {

		if (StringUtil.isBlank(classifyCode)) {
			return null;
		}

		String key = String.format(Constants.GOODS_CLASSIFY_CODE_NAME, classifyCode);
		String val = redisUtils.getString(key);

		if (StringUtil.isNotBlank(val)) {

			return val;
		} else {

			GoodsParent p = new GoodsParent();
			p.setSts(StsEnum.ACTIVE.getValue());
			p.setUuid(classifyCode);
			GoodsParent data = goodsParentMapper.selectOne(p);
			if (null == data) {

				String groupName = data.getGroupName();
				redisUtils.set(key, groupName, 5L, TimeUnit.MINUTES);
				return groupName;
			}
			return null;
		}
	}

	@Override
	public Store getStore() {

		String userUuid = TokenHelper.getUserUuid();
		StoreUser storeUser = storeUserMapper.selectByPrimaryKey(userUuid);
		if (null != storeUser) {
			String storeUuid = storeUser.getStoreUuid();
			if (org.apache.commons.lang3.StringUtils.isNotBlank(storeUuid)) {

				Store store = checkStoreAlreadyExist(storeUuid);
				return store;
			}
		}
		return null;
	}

	@Override
	public List<StoreGoodsClassifyRes> queryStoreGoodsClassifyRes(String storeUuid) {
		// ??????????????????????????????
		Goods params = new Goods();
		params.setSts(StsEnum.ACTIVE.getValue());
		params.setStoreUuid(storeUuid);
		List<Goods> goodsList = goodsMapper.select(params);
		if (!CollectionUtils.isEmpty(goodsList)) {

			Map<String, Set<String>> types = new HashMap<>();
			goodsList.stream().forEach(s -> {

				String parentType = s.getParentType();
				String subType = s.getSubType();
				if (types.containsKey(parentType)) {
					types.get(parentType).add(subType);
				} else {
					Set<String> subs = new HashSet<>();
					subs.add(subType);
					types.put(parentType, subs);
				}
			});

			if (null != types && !types.isEmpty()) {

				List<StoreGoodsClassifyRes> list = new ArrayList<>();
				types.forEach((parentType, val) -> {

					GoodsParent classifyOne = goodsParentMapper.selectByPrimaryKey(parentType);

					StoreGoodsClassifyRes dst = new StoreGoodsClassifyRes();
					BeanUtils.copyProperties(classifyOne, dst);
					list.add(dst);

					if (!CollectionUtils.isEmpty(val)) {

						List<ClassifyRes> subList = new ArrayList<>();
						val.stream().forEach(subType -> {

							GoodsParent classifyTwo = goodsParentMapper.selectByPrimaryKey(subType);
							ClassifyRes vv = new ClassifyRes();
							BeanUtils.copyProperties(classifyTwo, vv);

							Goods d = new Goods();
							d.setSts(StsEnum.ACTIVE.getValue());
							d.setStoreUuid(storeUuid);
							d.setParentType(parentType);
							d.setSubType(subType);
							List<Goods> goods = goodsMapper.select(d);
							if (!CollectionUtils.isEmpty(goods)) {
								List<GoodsRes> glist = new ArrayList<>();
								goods.stream().forEach(gg -> {
									GoodsRes gRes = new GoodsRes();
									BeanUtils.copyProperties(gg, gRes);
									glist.add(gRes);

									// ??????????????????
									GoodsImages i = new GoodsImages();
									i.setSts(StsEnum.ACTIVE.getValue());
									i.setGoodsUuid(gg.getUuid());
									List<GoodsImages> imgList = goodsImagesMapper.select(i);
									if (!CollectionUtils.isEmpty(imgList)) {

										List<GoodsImgRes> iList = new ArrayList<>();
										imgList.stream().forEach(img -> {
											GoodsImgRes irs = new GoodsImgRes();
											BeanUtils.copyProperties(img, irs);
											iList.add(irs);
										});
										gRes.setImgList(iList);
									}

									// ????????????????????????
									GoodsDetail dd = new GoodsDetail();
									dd.setGoodsUuid(gg.getUuid());
									dd.setSts(StsEnum.ACTIVE.getValue());
									List<GoodsDetail> dList = goodsDetailMapper.select(dd);
									if (!CollectionUtils.isEmpty(dList)) {

										List<GoodsDetailRes> sList = new ArrayList<>();
										dList.stream().forEach(detail -> {

											GoodsDetailRes drs = new GoodsDetailRes();
											BeanUtils.copyProperties(detail, drs);
											sList.add(drs);
										});
										gRes.setDetailList(sList);
									}
								});
								vv.setGoodsList(glist);
							}
							subList.add(vv);
							dst.setSubList(subList);

						});
					}

				});
				return list;
			}
		}
		return null;
	}

	@Override
	public ResultRes<StoreAccountRes> queryStoreAccount() {

		String userUuid = TokenHelper.getUserUuid();
		Integer userType = TokenHelper.getUserType();

		if (!UserTypeEnum.store.getType().equals(userType)) {
			throw new BusinessException(ResEnum.STORE_INVALID_TYPE);
		}

		StoreUser storeUser = storeUserMapper.selectByPrimaryKey(userUuid);
		if (null == storeUser) {

			throw new BusinessException(ResEnum.STORE_CONTACT_NOT_EXIST);
		}

		String storeUuid = storeUser.getStoreUuid();
		StoreAccount params = new StoreAccount();
		params.setSts(StsEnum.ACTIVE.getValue());
		params.setStoreUuid(storeUuid);
		StoreAccount storeAccount = storeAccountMapper.selectOne(params);
//        if(null == storeAccount){
//
//            log.error("???????????????????????????>>>params:{}", JSON.toJSONString(params));
//            throw new BusinessException(ResEnum.STORE_NO_ACCOUNT_AMT);
//        }

		StoreAccountRes res = new StoreAccountRes();
		BeanUtils.copyProperties(storeAccount, res);
		return ResultRes.success(res);
	}

	/**
	 * ?????????????????????uuid???????????????????????????
	 * @param storeUserUuid
	 * @return
	 */
	@Override
	public ResultRes<StoreUserRes> queryStoreUserInfo(String storeUserUuid) {
		StoreUserRes storeUserRes = storeUserMapper.getById(storeUserUuid);
		return ResultRes.success(storeUserRes);
	}

	/**
	 * ????????????????????????
	 * @return
	 */
	@Override
	public ResultRes<List<QueryShareStoreListRes>> queryShareStoreList(QueryShareStoreListReq param) {

		Integer distance = param.getDistance();
		if (distance == null) {

			// ????????????
			ResultRes<DictionaryRes> dictionaryRes = systemFeign.queryByUuid("10101");
			if (!dictionaryRes.isSuccess()) {
				throw new BusinessException(dictionaryRes.getCode(), dictionaryRes.getMsg());
			}
			distance = Integer.valueOf(dictionaryRes.getData().getLableValue());
		} else {
			distance = distance * 1000;
		}

		// ??????????????????????????????????????????
		List<QueryShareStoreListRes> shareStoreList = storeMapper.queryShareStoreList(distance, param.getLongitude(),
				param.getLatitude(), param.getBrandUuid(), param.getStoreType(),param.getShareStationTypeName());
		return ResultRes.success(shareStoreList);
	}

	@Override
	public CommentStaticsRes queryStoreCommentStatics(String storeUuid) {

		CommentStaticsRes rst = new CommentStaticsRes();
		rst.setScore(BigDecimal.valueOf(4.7));
		rst.setTotalNum(123);
		// TODO ???????????????
		return rst;
	}
}
