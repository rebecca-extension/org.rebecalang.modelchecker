package org.rebecalang.modelchecker.corerebeca.policy;

import org.rebecalang.modelchecker.corerebeca.MessageSpecification;
import org.rebecalang.modeltransformer.ril.rilinstructions.EndMsgSrvInstructionBean;
import org.rebecalang.modeltransformer.ril.rilinstructions.InstructionBean;

public class CoarseGrainedPolicy extends AbstractPolicy {

	public CoarseGrainedPolicy() {
		breakable = false;
	}
	
	@Override
	public void executedInstruction(InstructionBean ib) {
		breakable = ib instanceof EndMsgSrvInstructionBean;
	}

	@Override
	public void pick(MessageSpecification executableMessage) {
		breakable = false;
	}

}
