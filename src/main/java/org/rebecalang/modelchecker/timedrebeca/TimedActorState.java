package org.rebecalang.modelchecker.timedrebeca;

import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.ReactiveClassDeclaration;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.Type;
import org.rebecalang.compiler.utils.CodeCompilationException;
import org.rebecalang.modelchecker.corerebeca.*;
import org.rebecalang.modelchecker.corerebeca.policy.AbstractPolicy;
import org.rebecalang.modelchecker.corerebeca.rilinterpreter.InstructionInterpreter;
import org.rebecalang.modelchecker.corerebeca.rilinterpreter.InstructionUtilities;
import org.rebecalang.modelchecker.corerebeca.rilinterpreter.ProgramCounter;
import org.rebecalang.modeltransformer.ril.RILModel;
import org.rebecalang.modeltransformer.ril.corerebeca.rilinstruction.InstructionBean;

import java.util.ArrayList;
import java.util.PriorityQueue;

import static org.rebecalang.modelchecker.timedrebeca.TimedRebecaModelChecker.CURRENT_TIME;
import static org.rebecalang.modelchecker.timedrebeca.TimedRebecaModelChecker.RESUMING_TIME;

@SuppressWarnings("serial")
public class TimedActorState extends BaseActorState {
    private PriorityQueue<TimePriorityQueueItem<TimedMessageSpecification>> queue;

    public int getCurrentTime() {
        return (int) this.retrieveVariableValue(CURRENT_TIME);
    }

    public void setCurrentTime(int currentTime) {
        this.setVariableValue(CURRENT_TIME, currentTime);
    }

    public int getResumingTime() {
        return (int) this.retrieveVariableValue(RESUMING_TIME);
    }

    public void setResumingTime(int currentTime) {
        this.setVariableValue(RESUMING_TIME, currentTime);
    }

    public void increaseResumingTime(int delay) {
        this.setVariableValue(RESUMING_TIME, getCurrentTime() + delay);
    }

    public TimedActorState() {
        setQueue(new PriorityQueue<TimePriorityQueueItem<TimedMessageSpecification>>());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((actorScopeStack == null) ? 0 : actorScopeStack.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((queue == null) ? 0 : queue.hashCode());
        result = prime * result + ((typeName == null) ? 0 : typeName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TimedActorState other = (TimedActorState) obj;
        if (actorScopeStack == null) {
            if (other.actorScopeStack != null)
                return false;
        } else if (!actorScopeStack.equals(other.actorScopeStack))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (queue == null) {
            if (other.queue != null)
                return false;
        } else if (!queue.equals(other.queue))
            return false;
        if (typeName == null) {
            return other.typeName == null;
        } else return typeName.equals(other.typeName);
    }

    public PriorityQueue<TimePriorityQueueItem<TimedMessageSpecification>> getQueue() {
        return queue;
    }

    public void setQueue(PriorityQueue<TimePriorityQueueItem<TimedMessageSpecification>> queue) {
        this.queue = queue;
    }

    @Override
    public void addToQueue(MessageSpecification msgSpec) {
        TimedMessageSpecification timedMsgSpec = ((TimedMessageSpecification) msgSpec);
        queue.add(new TimePriorityQueueItem(timedMsgSpec.minStartTime, timedMsgSpec));
    }

    @Override
    public boolean actorQueueIsEmpty() {
        return queue.isEmpty();
    }

    public void resumeExecution(State state, RILModel transformedRILModel, AbstractPolicy policy) {
        do {
            ProgramCounter pc = getPC();

            String methodName = pc.getMethodName();
            Type currentType = null;
            try {
                currentType = typeSystem.getType(pc.getMethodName().split("\\.")[0]);
            } catch (CodeCompilationException e) {
                e.printStackTrace();
            }

            while (transformedRILModel.getInstructionList(methodName) == null) {
                try {
                    ReactiveClassDeclaration rcd = (ReactiveClassDeclaration)typeSystem.getMetaData(currentType);
                    if (rcd.getExtends() == null)
                        break;
                    currentType = rcd.getExtends();
                    methodName = currentType.getTypeName() + "." + pc.getMethodName().split("\\.")[1];
                } catch (CodeCompilationException e) {
                    e.printStackTrace();
                }
            }

            int lineNumber = pc.getLineNumber();
            InstructionBean instruction = transformedRILModel.getInstructionList(methodName).get(lineNumber);
            InstructionInterpreter interpreter = StatementInterpreterContainer.getInstance().retrieveInterpreter(instruction);
            policy.executedInstruction(instruction);
            interpreter.interpret(instruction, this, state);
        } while (!policy.isBreakable());
    }

    public void execute(State state, RILModel transformedRILModel, AbstractPolicy policy, TimedMessageSpecification executableMessage) {
        policy.pick(executableMessage);
//        String msgName = getTypeName() + "." + executableMessage.getMessageName().split("\\.")[1];
//        if (!transformedRILModel.getMethodNames().contains(msgName)) {
//            msgName = executableMessage.getMessageName();
//        }

        String msgName = executableMessage.getMessageName();
        Type currentType = null;
        try {
            currentType = typeSystem.getType(executableMessage.getMessageName().split("\\.")[0]);
        } catch (CodeCompilationException e) {
            e.printStackTrace();
        }

        while (!transformedRILModel.getMethodNames().contains(msgName)) {
            try {
                ReactiveClassDeclaration rcd = (ReactiveClassDeclaration)typeSystem.getMetaData(currentType);
                if (rcd.getExtends() == null)
                    break;
                currentType = rcd.getExtends();
                msgName = currentType.getTypeName() + "." + executableMessage.getMessageName().split("\\.")[1];
            } catch (CodeCompilationException e) {
                e.printStackTrace();
            }
        }

        String relatedRebecType = msgName.split("\\.")[0];
        actorScopeStack.pushInScopeStack(getTypeName(), relatedRebecType);
        addVariableToRecentScope("sender", executableMessage.getSenderActorState());
        initializePC(msgName, 0);
        resumeExecution(state, transformedRILModel, policy);
    }

    @Override
    public MessageSpecification getMessage() {
        return queue.peek() != null ? queue.peek().getItem() : null;
    }

    public int firstTimeActorCanPeekNewMessage() {
        if (this.variableIsDefined(InstructionUtilities.PC_STRING)) {
            throw new RuntimeException("This version supports coarse grained execution.");
        } else {
            if (!this.actorQueueIsEmpty()) {
                int resumingTime = getResumingTime();
                int firstMsgTime = queue.peek().getItem().minStartTime;
                return Math.max(resumingTime, firstMsgTime);
            }
        }
        return Integer.MAX_VALUE;
    }

    public ArrayList<TimedMessageSpecification> getEnabledMsgs(int enablingTime) throws ModelCheckingException {
        ArrayList<TimedMessageSpecification> enabledMsgs = new ArrayList<>();
        while (this.queue.peek() != null && this.queue.peek().getTime() <= enablingTime) {
            TimedMessageSpecification curMsg = this.queue.poll().getItem();
            if (curMsg.maxStartTime < getCurrentTime()) throw new ModelCheckingException("Deadlock");
            enabledMsgs.add(curMsg);
        }
        return enabledMsgs;
    }
}
