/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.map.service.sms;

import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.ss7.map.MAPDialogImpl;
import org.mobicents.protocols.ss7.map.MAPProviderImpl;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContext;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContextName;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContextVersion;
import org.mobicents.protocols.ss7.map.api.MAPException;
import org.mobicents.protocols.ss7.map.api.MAPOperationCode;
import org.mobicents.protocols.ss7.map.api.primitives.AddressString;
import org.mobicents.protocols.ss7.map.api.primitives.IMSI;
import org.mobicents.protocols.ss7.map.api.primitives.ISDNAddressString;
import org.mobicents.protocols.ss7.map.api.primitives.MAPExtensionContainer;
import org.mobicents.protocols.ss7.map.api.service.sms.LocationInfoWithLMSI;
import org.mobicents.protocols.ss7.map.api.service.sms.MAPServiceSms;
import org.mobicents.protocols.ss7.map.api.service.sms.MAPDialogSms;
import org.mobicents.protocols.ss7.map.api.service.sms.MWStatus;
import org.mobicents.protocols.ss7.map.api.service.sms.SMDeliveryOutcome;
import org.mobicents.protocols.ss7.map.api.service.sms.SM_RP_DA;
import org.mobicents.protocols.ss7.map.api.service.sms.SM_RP_MTI;
import org.mobicents.protocols.ss7.map.api.service.sms.SM_RP_OA;
import org.mobicents.protocols.ss7.tcap.api.TCAPException;
import org.mobicents.protocols.ss7.tcap.api.tc.component.InvokeClass;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResultLast;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCode;
import org.mobicents.protocols.ss7.tcap.asn.comp.Parameter;

/**
 * 
 * @author sergey vetyutnev
 * 
 */
public class MAPDialogSmsImpl extends MAPDialogImpl implements MAPDialogSms {

	protected MAPDialogSmsImpl(MAPApplicationContext appCntx, Dialog tcapDialog, MAPProviderImpl mapProviderImpl, MAPServiceSms mapService,
			AddressString origReference, AddressString destReference) {
		super(appCntx, tcapDialog, mapProviderImpl, mapService, origReference, destReference);
	}

	
	@Override
	public Long addForwardShortMessageRequest(SM_RP_DA sm_RP_DA, SM_RP_OA sm_RP_OA, byte[] sm_RP_UI, boolean moreMessagesToSend) throws MAPException {
		return addForwardShortMessageRequest(_Timer_Default, sm_RP_DA, sm_RP_OA, sm_RP_UI, moreMessagesToSend);
	}

	@Override
	public Long addForwardShortMessageRequest(int customInvokeTimeout, SM_RP_DA sm_RP_DA, SM_RP_OA sm_RP_OA, byte[] sm_RP_UI, boolean moreMessagesToSend)
			throws MAPException {

		if ((this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgMORelayContext && this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgMTRelayContext)
				|| (this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version1 && this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version2))
			throw new MAPException("Bad application context name for addForwardShortMessageRequest: must be shortMsgMORelayContext_V1 or V2 or shortMsgMTRelayContext_V1 or V2");
		
		if (this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version2)
			moreMessagesToSend = false;

		Invoke invoke = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCInvokeRequest();
		if (customInvokeTimeout == _Timer_Default)
			invoke.setTimeout(_Timer_ml);
		else
			invoke.setTimeout(customInvokeTimeout);

		OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
		oc.setLocalOperationCode((long)MAPOperationCode.mo_forwardSM);
		invoke.setOperationCode(oc);

		ForwardShortMessageRequestIndicationImpl req = new ForwardShortMessageRequestIndicationImpl(sm_RP_DA, sm_RP_OA, sm_RP_UI, moreMessagesToSend);
		AsnOutputStream aos = new AsnOutputStream();
		req.encodeData(aos);

		Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
		p.setTagClass(req.getTagClass());
		p.setPrimitive(req.getIsPrimitive());
		p.setTag(req.getTag());
		p.setData(aos.toByteArray());
		invoke.setParameter(p);

		Long invokeId;
		try {
			invokeId = this.tcapDialog.getNewInvokeId();
			invoke.setInvokeId(invokeId);
		} catch (TCAPException e) {
			throw new MAPException(e.getMessage(), e);
		}

		this.sendInvokeComponent(invoke);
		
		return invokeId;
	}
	
	@Override
	public void addForwardShortMessageResponse(long invokeId) throws MAPException {

		if ((this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgMORelayContext && this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgMTRelayContext)
				|| (this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version1 && this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version2))
			throw new MAPException("Bad application context name for addForwardShortMessageResponse: must be shortMsgMORelayContext_V1 or V2 or shortMsgMTRelayContext_V1 or V2");
		
		ReturnResultLast resultLast = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCResultLastRequest();

		resultLast.setInvokeId(invokeId);

		// we need not Operation Code because no answer

		this.sendReturnResultLastComponent(resultLast);
	}

	@Override
	public Long addMoForwardShortMessageRequest(SM_RP_DA sm_RP_DA, SM_RP_OA sm_RP_OA, byte[] sm_RP_UI, MAPExtensionContainer extensionContainer, IMSI imsi)
			throws MAPException {
		return addMoForwardShortMessageRequest(_Timer_Default, sm_RP_DA, sm_RP_OA, sm_RP_UI, extensionContainer, imsi);
	}

	@Override
	public Long addMoForwardShortMessageRequest(int customInvokeTimeout, SM_RP_DA sm_RP_DA, SM_RP_OA sm_RP_OA, byte[] sm_RP_UI,
			MAPExtensionContainer extensionContainer, IMSI imsi) throws MAPException {

		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgMORelayContext
				|| this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version3)
			throw new MAPException("Bad application context name for addMoForwardShortMessageRequest: must be shortMsgMORelayContext_V3");

		Invoke invoke = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCInvokeRequest();
		if (customInvokeTimeout == _Timer_Default)
			invoke.setTimeout(_Timer_ml);
		else
			invoke.setTimeout(customInvokeTimeout);

		OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
		oc.setLocalOperationCode((long)MAPOperationCode.mo_forwardSM);
		invoke.setOperationCode(oc);

		MoForwardShortMessageRequestIndicationImpl req = new MoForwardShortMessageRequestIndicationImpl(sm_RP_DA, sm_RP_OA, sm_RP_UI, extensionContainer, imsi);
		AsnOutputStream aos = new AsnOutputStream();
		req.encodeData(aos);

		Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
		p.setTagClass(req.getTagClass());
		p.setPrimitive(req.getIsPrimitive());
		p.setTag(req.getTag());
		p.setData(aos.toByteArray());
		invoke.setParameter(p);

		Long invokeId;
		try {
			invokeId = this.tcapDialog.getNewInvokeId();
			invoke.setInvokeId(invokeId);
		} catch (TCAPException e) {
			throw new MAPException(e.getMessage(), e);
		}

		this.sendInvokeComponent(invoke);
		
		return invokeId;
	}
	
	@Override
	public void addMoForwardShortMessageResponse(long invokeId, byte[] sm_RP_UI, MAPExtensionContainer extensionContainer) throws MAPException {

		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgMORelayContext
				|| this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version3)
			throw new MAPException("Bad application context name for addMoForwardShortMessageResponse: must be shortMsgMORelayContext_V3");
		
		ReturnResultLast resultLast = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCResultLastRequest();

		resultLast.setInvokeId(invokeId);

		// Operation Code
		OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
		oc.setLocalOperationCode((long) MAPOperationCode.mo_forwardSM);
		resultLast.setOperationCode(oc);

		if (sm_RP_UI != null || extensionContainer != null) {

			MoForwardShortMessageResponseIndicationImpl req = new MoForwardShortMessageResponseIndicationImpl(sm_RP_UI, extensionContainer);
			AsnOutputStream aos = new AsnOutputStream();
			req.encodeData(aos);

			Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
			p.setTagClass(req.getTagClass());
			p.setPrimitive(req.getIsPrimitive());
			p.setTag(req.getTag());
			p.setData(aos.toByteArray());
			resultLast.setParameter(p);
		}

		this.sendReturnResultLastComponent(resultLast);
	}

	@Override
	public Long addMtForwardShortMessageRequest(SM_RP_DA sm_RP_DA, SM_RP_OA sm_RP_OA, byte[] sm_RP_UI, boolean moreMessagesToSend,
			MAPExtensionContainer extensionContainer) throws MAPException {
		return this.addMtForwardShortMessageRequest(_Timer_Default, sm_RP_DA, sm_RP_OA, sm_RP_UI, moreMessagesToSend, extensionContainer);
	}
	
	@Override
	public Long addMtForwardShortMessageRequest(int customInvokeTimeout, SM_RP_DA sm_RP_DA, SM_RP_OA sm_RP_OA, byte[] sm_RP_UI, boolean moreMessagesToSend,
			MAPExtensionContainer extensionContainer) throws MAPException {

		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgMTRelayContext
				|| this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version3)
			throw new MAPException("Bad application context name for addMtForwardShortMessageRequest: must be shortMsgMTRelayContext_V3");
		
		Invoke invoke = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCInvokeRequest();
		if (customInvokeTimeout == _Timer_Default)
			invoke.setTimeout(_Timer_ml);
		else
			invoke.setTimeout(customInvokeTimeout);

		try {
			// Operation Code
			OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
			oc.setLocalOperationCode((long)MAPOperationCode.mt_forwardSM);
			invoke.setOperationCode(oc);
			
			MtForwardShortMessageRequestIndicationImpl req = new MtForwardShortMessageRequestIndicationImpl(sm_RP_DA, sm_RP_OA, sm_RP_UI, moreMessagesToSend,
					extensionContainer);
			AsnOutputStream aos = new AsnOutputStream();
			req.encodeData(aos);

			Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
			p.setTagClass(req.getTagClass());
			p.setPrimitive(req.getIsPrimitive());
			p.setTag(req.getTag());
			p.setData(aos.toByteArray());
			invoke.setParameter(p);
			
			Long invokeId = this.tcapDialog.getNewInvokeId();
			invoke.setInvokeId(invokeId);

			this.sendInvokeComponent(invoke);
			
			return invokeId;

		} catch (TCAPException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}

	@Override
	public void addMtForwardShortMessageResponse(long invokeId, byte[] sm_RP_UI, MAPExtensionContainer extensionContainer) throws MAPException {

		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgMTRelayContext
				|| this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version3)
			throw new MAPException("Bad application context name for addMtForwardShortMessageResponse: must be shortMsgMTRelayContext_V3");
		
		ReturnResultLast resultLast = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCResultLastRequest();

		resultLast.setInvokeId(invokeId);

		// Operation Code
		OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
		oc.setLocalOperationCode((long) MAPOperationCode.mt_forwardSM);
		resultLast.setOperationCode(oc);

		if (sm_RP_UI != null || extensionContainer != null) {

			MtForwardShortMessageResponseIndicationImpl resp = new MtForwardShortMessageResponseIndicationImpl(sm_RP_UI, extensionContainer);
			AsnOutputStream aos = new AsnOutputStream();
			resp.encodeData(aos);

			Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
			p.setTagClass(resp.getTagClass());
			p.setPrimitive(resp.getIsPrimitive());
			p.setTag(resp.getTag());
			p.setData(aos.toByteArray());
			resultLast.setParameter(p);
		}

		this.sendReturnResultLastComponent(resultLast);
	}

	@Override
	public Long addSendRoutingInfoForSMRequest(ISDNAddressString msisdn, boolean sm_RP_PRI, AddressString serviceCentreAddress,
			MAPExtensionContainer extensionContainer, boolean gprsSupportIndicator, SM_RP_MTI sM_RP_MTI, byte[] sM_RP_SMEA) throws MAPException {
		return this.addSendRoutingInfoForSMRequest(_Timer_Default, msisdn, sm_RP_PRI, serviceCentreAddress, extensionContainer, gprsSupportIndicator,
				sM_RP_MTI, sM_RP_SMEA);
	}

	@Override
	public Long addSendRoutingInfoForSMRequest(int customInvokeTimeout, ISDNAddressString msisdn, boolean sm_RP_PRI, AddressString serviceCentreAddress,
			MAPExtensionContainer extensionContainer, boolean gprsSupportIndicator, SM_RP_MTI sM_RP_MTI, byte[] sM_RP_SMEA) throws MAPException {

		MAPApplicationContextVersion vers = this.appCntx.getApplicationContextVersion();
		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgGatewayContext
				|| (vers != MAPApplicationContextVersion.version1 && vers != MAPApplicationContextVersion.version2 && vers != MAPApplicationContextVersion.version3))
			throw new MAPException("Bad application context name for addSendRoutingInfoForSMRequest: must be shortMsgGatewayContext_V1, V2 or V3");

		Invoke invoke = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCInvokeRequest();
		if (customInvokeTimeout == _Timer_Default)
			invoke.setTimeout(_Timer_m);
		else
			invoke.setTimeout(customInvokeTimeout);
		
		// Operation Code
		OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
		oc.setLocalOperationCode((long)MAPOperationCode.sendRoutingInfoForSM);
		invoke.setOperationCode(oc);

		try {
			SendRoutingInfoForSMRequestIndicationImpl req = new SendRoutingInfoForSMRequestIndicationImpl(msisdn, sm_RP_PRI, serviceCentreAddress,
					extensionContainer, gprsSupportIndicator, sM_RP_MTI, sM_RP_SMEA);
			AsnOutputStream aos = new AsnOutputStream();
			req.encodeData(aos);

			Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
			p.setTagClass(req.getTagClass());
			p.setPrimitive(req.getIsPrimitive());
			p.setTag(req.getTag());
			p.setData(aos.toByteArray());
			invoke.setParameter(p);

			Long invokeId = this.tcapDialog.getNewInvokeId();
			invoke.setInvokeId(invokeId);

			this.sendInvokeComponent(invoke);
			
			return invokeId;

		} catch (TCAPException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}
	
	@Override
	public void addSendRoutingInfoForSMResponse(long invokeId, IMSI imsi, LocationInfoWithLMSI locationInfoWithLMSI, MAPExtensionContainer extensionContainer)
			throws MAPException {

		MAPApplicationContextVersion vers = this.appCntx.getApplicationContextVersion();
		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgGatewayContext
				|| (vers != MAPApplicationContextVersion.version1 && vers != MAPApplicationContextVersion.version2 && vers != MAPApplicationContextVersion.version3))
			throw new MAPException("Bad application context name for addSendRoutingInfoForSMResponse: must be shortMsgGatewayContext_V1, V2 or V3");
		
		ReturnResultLast resultLast = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCResultLastRequest();

		resultLast.setInvokeId(invokeId);

		// Operation Code
		OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
		oc.setLocalOperationCode((long) MAPOperationCode.sendRoutingInfoForSM);
		resultLast.setOperationCode(oc);

		SendRoutingInfoForSMResponseIndicationImpl resp = new SendRoutingInfoForSMResponseIndicationImpl(imsi, locationInfoWithLMSI, extensionContainer);
		AsnOutputStream aos = new AsnOutputStream();
		resp.encodeData(aos);

		Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
		p.setTagClass(resp.getTagClass());
		p.setPrimitive(resp.getIsPrimitive());
		p.setTag(resp.getTag());
		p.setData(aos.toByteArray());
		resultLast.setParameter(p);		
		
		this.sendReturnResultLastComponent(resultLast);
	}

	@Override
	public Long addReportSMDeliveryStatusRequest(ISDNAddressString msisdn, AddressString serviceCentreAddress, SMDeliveryOutcome sMDeliveryOutcome,
			Integer absentSubscriberDiagnosticSM, MAPExtensionContainer extensionContainer, boolean gprsSupportIndicator, boolean deliveryOutcomeIndicator,
			SMDeliveryOutcome additionalSMDeliveryOutcome, Integer additionalAbsentSubscriberDiagnosticSM) throws MAPException {
		return this.addReportSMDeliveryStatusRequest(_Timer_Default, msisdn, serviceCentreAddress, sMDeliveryOutcome, absentSubscriberDiagnosticSM,
				extensionContainer, gprsSupportIndicator, deliveryOutcomeIndicator, additionalSMDeliveryOutcome, additionalAbsentSubscriberDiagnosticSM);
	}

	@Override
	public Long addReportSMDeliveryStatusRequest(int customInvokeTimeout, ISDNAddressString msisdn, AddressString serviceCentreAddress,
			SMDeliveryOutcome sMDeliveryOutcome, Integer absentSubscriberDiagnosticSM, MAPExtensionContainer extensionContainer, boolean gprsSupportIndicator,
			boolean deliveryOutcomeIndicator, SMDeliveryOutcome additionalSMDeliveryOutcome, Integer additionalAbsentSubscriberDiagnosticSM)
			throws MAPException {

		MAPApplicationContextVersion vers = this.appCntx.getApplicationContextVersion();
		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgGatewayContext
				|| (vers != MAPApplicationContextVersion.version1 && vers != MAPApplicationContextVersion.version2 && vers != MAPApplicationContextVersion.version3))
			throw new MAPException("Bad application context name for addReportSMDeliveryStatusRequest: must be shortMsgGatewayContext_V1, V2 or V3");
		
		if (msisdn == null || serviceCentreAddress == null || sMDeliveryOutcome == null)
			throw new MAPException("msisdn, serviceCentreAddress and sMDeliveryOutcome must not be null");
		
		Invoke invoke = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCInvokeRequest();
		if (customInvokeTimeout == _Timer_Default)
			invoke.setTimeout(_Timer_s);
		else
			invoke.setTimeout(customInvokeTimeout);
		
		try {
			// Operation Code
			OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
			oc.setLocalOperationCode((long)MAPOperationCode.reportSM_DeliveryStatus);
			invoke.setOperationCode(oc);
			
			ReportSMDeliveryStatusRequestIndicationImpl req = new ReportSMDeliveryStatusRequestIndicationImpl(this.getApplicationContext()
					.getApplicationContextVersion().getVersion(), msisdn, serviceCentreAddress, sMDeliveryOutcome, absentSubscriberDiagnosticSM,
					extensionContainer, gprsSupportIndicator, deliveryOutcomeIndicator, additionalSMDeliveryOutcome, additionalAbsentSubscriberDiagnosticSM);
			AsnOutputStream aos = new AsnOutputStream();
			req.encodeData(aos);

			Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
			p.setTagClass(req.getTagClass());
			p.setPrimitive(req.getIsPrimitive());
			p.setTag(req.getTag());
			p.setData(aos.toByteArray());
			invoke.setParameter(p);

			Long invokeId = this.tcapDialog.getNewInvokeId();
			invoke.setInvokeId(invokeId);

			this.sendInvokeComponent(invoke);
			
			return invokeId;

		} catch (TCAPException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}
	
	@Override
	public void addReportSMDeliveryStatusResponse(long invokeId, ISDNAddressString storedMSISDN, MAPExtensionContainer extensionContainer) throws MAPException {

		MAPApplicationContextVersion vers = this.appCntx.getApplicationContextVersion();
		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgGatewayContext
				|| (vers != MAPApplicationContextVersion.version1 && vers != MAPApplicationContextVersion.version2 && vers != MAPApplicationContextVersion.version3))
			throw new MAPException("Bad application context name for addReportSMDeliveryStatusResponse: must be shortMsgGatewayContext_V1, V2 or V3");
		
		ReturnResultLast resultLast = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCResultLastRequest();

		resultLast.setInvokeId(invokeId);

		// Operation Code
		OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
		oc.setLocalOperationCode((long) MAPOperationCode.reportSM_DeliveryStatus);
		resultLast.setOperationCode(oc);

		if (storedMSISDN != null || extensionContainer != null) {

			ReportSMDeliveryStatusResponseIndicationImpl resp = new ReportSMDeliveryStatusResponseIndicationImpl(storedMSISDN, extensionContainer);
			AsnOutputStream aos = new AsnOutputStream();
			resp.encodeData(aos);

			Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
			p.setTagClass(resp.getTagClass());
			p.setPrimitive(resp.getIsPrimitive());
			p.setTag(resp.getTag());
			p.setData(aos.toByteArray());
			resultLast.setParameter(p);
		}

		this.sendReturnResultLastComponent(resultLast);
	}

	@Override
	public Long addInformServiceCentreRequest(ISDNAddressString storedMSISDN, MWStatus mwStatus, MAPExtensionContainer extensionContainer,
			Integer absentSubscriberDiagnosticSM, Integer additionalAbsentSubscriberDiagnosticSM) throws MAPException {
		return this.addInformServiceCentreRequest(_Timer_Default, storedMSISDN, mwStatus, extensionContainer, absentSubscriberDiagnosticSM,
				additionalAbsentSubscriberDiagnosticSM);
	}

	@Override
	public Long addInformServiceCentreRequest(int customInvokeTimeout, ISDNAddressString storedMSISDN, MWStatus mwStatus,
			MAPExtensionContainer extensionContainer, Integer absentSubscriberDiagnosticSM, Integer additionalAbsentSubscriberDiagnosticSM) throws MAPException {

		MAPApplicationContextVersion vers = this.appCntx.getApplicationContextVersion();
		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgGatewayContext
				|| (vers != MAPApplicationContextVersion.version2 && vers != MAPApplicationContextVersion.version3))
			throw new MAPException("Bad application context name for addInformServiceCentreRequest: must be shortMsgGatewayContext_V2 or V3");
		
		Invoke invoke = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCInvokeRequest(InvokeClass.Class4);
		if (customInvokeTimeout == _Timer_Default)
			invoke.setTimeout(_Timer_s);
		else
			invoke.setTimeout(customInvokeTimeout);

		try {
			// Operation Code
			OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
			oc.setLocalOperationCode((long)MAPOperationCode.informServiceCentre);
			invoke.setOperationCode(oc);

			InformServiceCentreRequestIndicationImpl req = new InformServiceCentreRequestIndicationImpl(storedMSISDN, mwStatus, extensionContainer,
					absentSubscriberDiagnosticSM, additionalAbsentSubscriberDiagnosticSM);
			AsnOutputStream aos = new AsnOutputStream();
			req.encodeData(aos);
			
			Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
			p.setTagClass(req.getTagClass());
			p.setPrimitive(req.getIsPrimitive());
			p.setTag(req.getTag());
			p.setData(aos.toByteArray());
			invoke.setParameter(p);

			Long invokeId = this.tcapDialog.getNewInvokeId();
			invoke.setInvokeId(invokeId);

			this.sendInvokeComponent(invoke);
			
			return invokeId;

		} catch (TCAPException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}
	
	@Override
	public Long addAlertServiceCentreRequest(ISDNAddressString msisdn, AddressString serviceCentreAddress) throws MAPException {
		return this.addAlertServiceCentreRequest(_Timer_Default, msisdn, serviceCentreAddress);
	}
	
	@Override
	public Long addAlertServiceCentreRequest(int customInvokeTimeout, ISDNAddressString msisdn, AddressString serviceCentreAddress) throws MAPException {

		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgAlertContext
				|| (this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version1 && this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version2))
			throw new MAPException("Bad application context name for addAlertServiceCentreRequest: must be shortMsgAlertContext_V1 or V2");
		
		Invoke invoke = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCInvokeRequest();
		if (customInvokeTimeout == _Timer_Default)
			invoke.setTimeout(_Timer_s);
		else
			invoke.setTimeout(customInvokeTimeout);

		try {
			// Operation Code
			OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
			if (this.appCntx.getApplicationContextVersion() == MAPApplicationContextVersion.version1)
				oc.setLocalOperationCode((long) MAPOperationCode.alertServiceCentreWithoutResult);
			else
				oc.setLocalOperationCode((long) MAPOperationCode.alertServiceCentre);
			invoke.setOperationCode(oc);

			AlertServiceCentreRequestIndicationImpl req = new AlertServiceCentreRequestIndicationImpl(msisdn, serviceCentreAddress);
			AsnOutputStream aos = new AsnOutputStream();
			req.encodeData(aos);
			
			Parameter p = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createParameter();
			p.setTagClass(req.getTagClass());
			p.setPrimitive(req.getIsPrimitive());
			p.setTag(req.getTag());
			p.setData(aos.toByteArray());
			invoke.setParameter(p);

			Long invokeId = this.tcapDialog.getNewInvokeId();
			invoke.setInvokeId(invokeId);

			this.sendInvokeComponent(invoke);
			
			return invokeId;

		} catch (TCAPException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}

	@Override
	public void addAlertServiceCentreResponse(long invokeId) throws MAPException {

		if (this.appCntx.getApplicationContextName() != MAPApplicationContextName.shortMsgAlertContext
				|| (this.appCntx.getApplicationContextVersion() != MAPApplicationContextVersion.version2))
			throw new MAPException("Bad application context name for addAlertServiceCentreResponse: must be shortMsgAlertContext_V2");
		
		ReturnResultLast resultLast = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createTCResultLastRequest();

		resultLast.setInvokeId(invokeId);

		// we need not Operation Code because no answer
//		OperationCode oc = this.mapProviderImpl.getTCAPProvider().getComponentPrimitiveFactory().createOperationCode();
//		oc.setLocalOperationCode((long) MAPOperationCode.alertServiceCentre);
//		resultLast.setOperationCode(oc);

		this.sendReturnResultLastComponent(resultLast);
	}
}
