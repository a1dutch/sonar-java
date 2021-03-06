/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

import java.util.List;

@Rule(
  key = "S2165",
  name = "\"finalize\" should not set fields to \"null\"",
  tags = {"clumsy", "performance"},
  priority = Priority.MAJOR)
@ActivatedByDefault
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.CPU_EFFICIENCY)
@SqaleConstantRemediation("5min")
public class FinalizeFieldsSetCheck extends SubscriptionBaseVisitor {

  @Override
  public List<Kind> nodesToVisit() {
    return ImmutableList.of(Kind.METHOD);
  }

  @Override
  public void visitNode(Tree tree) {
    MethodTree methodTree = (MethodTree) tree;
    if (hasSemantic() && isFinalizeDeclaration(methodTree)) {
      methodTree.accept(new AssignmentVisitor());
    }
  }

  private boolean isFinalizeDeclaration(MethodTree tree) {
    return isMethodNamedFinalize(tree) && hasNoParameters(tree);
  }

  private boolean isMethodNamedFinalize(MethodTree tree) {
    return "finalize".equals(tree.simpleName().name());
  }

  private boolean hasNoParameters(MethodTree tree) {
    return tree.parameters().isEmpty();
  }

  private class AssignmentVisitor extends BaseTreeVisitor {
    @Override
    public void visitClass(ClassTree tree) {
      // Do not visit inner classes as their methods will be visited by main visitor
    }

    @Override
    public void visitAssignmentExpression(AssignmentExpressionTree tree) {
      if (isFieldAssignment(tree) && isNullAssignment(tree)) {
        addIssue(tree, "Remove this nullification of \"" + getFieldName(tree) + "\".");
      }
    }

    private boolean isFieldAssignment(AssignmentExpressionTree tree) {
      ExpressionTree variable = tree.variable();
      if (variable.is(Kind.MEMBER_SELECT)) {
        MemberSelectExpressionTree memberSelectExpressionTree = (MemberSelectExpressionTree) variable;
        if (!isThis(memberSelectExpressionTree.expression())) {
          return false;
        }
        variable = memberSelectExpressionTree.identifier();
      }
      if (variable.is(Kind.IDENTIFIER)) {
        Symbol variableSymbol = getSemanticModel().getReference((IdentifierTree) variable);
        return variableSymbol != null && variableSymbol.owner().isTypeSymbol();
      }
      return false;
    }

    private boolean isThis(ExpressionTree tree) {
      return tree.is(Kind.IDENTIFIER) && "this".equals(((IdentifierTree) tree).name());
    }

    private boolean isNullAssignment(AssignmentExpressionTree tree) {
      return tree.expression().is(Kind.NULL_LITERAL);
    }

    private String getFieldName(AssignmentExpressionTree tree) {
      ExpressionTree variable = tree.variable();
      if (variable.is(Kind.MEMBER_SELECT)) {
        variable = ((MemberSelectExpressionTree) variable).identifier();
      }
      return ((IdentifierTree) variable).name();
    }
  }
}
