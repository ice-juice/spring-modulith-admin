package com.sma.admin.common;

import java.util.List;

public record TreeNode(Long id, Long parentId, String label, boolean disabled, List<TreeNode> children) {
    public TreeNode { children = List.copyOf(children); }
}
