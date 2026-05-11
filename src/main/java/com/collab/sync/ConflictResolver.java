package com.collab.sync;

import com.collab.core.EditOperation;
import java.util.ArrayList;
import java.util.List;

public class ConflictResolver {

    public EditOperation resolveConflict(EditOperation local, EditOperation remote) {
        if (local == null) return remote;
        if (remote == null) return local;

        if (local.getTimestamp() > remote.getTimestamp()) {
            return local.transform(remote);
        } else {
            return remote.transform(local);
        }
    }

    public List<EditOperation> resolveMultiple(List<EditOperation> localOps, List<EditOperation> remoteOps) {
        List<EditOperation> result = new ArrayList<>();
        List<EditOperation> allOps = new ArrayList<>(localOps);
        allOps.addAll(remoteOps);
        allOps.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        EditOperation lastOp = null;
        for (EditOperation op : allOps) {
            if (lastOp == null) {
                result.add(op);
            } else {
                result.add(op.transform(lastOp));
            }
            lastOp = op;
        }
        return result;
    }

    public boolean isConflicting(EditOperation op1, EditOperation op2) {
        if (op1.getType() == EditOperation.OpType.INSERT && 
            op2.getType() == EditOperation.OpType.INSERT) {
            return Math.abs(op1.getPosition() - op2.getPosition()) < 10;
        }
        if (op1.getType() == EditOperation.OpType.DELETE && 
            op2.getType() == EditOperation.OpType.DELETE) {
            int start1 = op1.getPosition();
            int end1 = start1 + op1.getText().length();
            int start2 = op2.getPosition();
            int end2 = start2 + op2.getText().length();
            return (start1 <= end2 && start2 <= end1);
        }
        return false;
    }
}