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
import org.sonar.java.model.declaration.ClassTreeImpl;
import org.sonar.java.model.expression.MethodInvocationTreeImpl;
import org.sonar.java.resolve.Type;
import org.sonar.java.resolve.Types;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

import java.util.List;

@Rule(
  key = "S2388",
  name = "Inner class calls to super class methods should be unambiguous",
  tags = {"pitfall"},
  priority = Priority.MAJOR)
@ActivatedByDefault
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.UNDERSTANDABILITY)
@SqaleConstantRemediation("5min")
public class CallSuperMethodFromInnerClassCheck extends SubscriptionBaseVisitor {

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.CLASS, Tree.Kind.INTERFACE);
  }

  @Override
  public void visitNode(Tree tree) {
    ClassTreeImpl classTree = (ClassTreeImpl) tree;
    org.sonar.java.resolve.Symbol.TypeSymbol classSymbol = classTree.getSymbol();
    if (classSymbol != null && isInnerClass(classSymbol) && !extendsOuterClass(classSymbol)) {
      classTree.accept(new MethodInvocationVisitor(classSymbol));
    }
  }

  private boolean isInnerClass(Symbol symbol) {
    return symbol.owner().isTypeSymbol();
  }

  private boolean extendsOuterClass(org.sonar.java.resolve.Symbol.TypeSymbol classSymbol) {
    return classSymbol.getSuperclass() != null && classSymbol.getSuperclass().equals(classSymbol.owner().getType());
  }

  private class MethodInvocationVisitor extends BaseTreeVisitor {
    private final org.sonar.java.resolve.Symbol.TypeSymbol classSymbol;

    public MethodInvocationVisitor(org.sonar.java.resolve.Symbol.TypeSymbol classSymbol) {
      this.classSymbol = classSymbol;
    }

    @Override
    public void visitMethodInvocation(MethodInvocationTree tree) {
      MethodInvocationTreeImpl mit = (MethodInvocationTreeImpl) tree;
      Symbol symbol = mit.getSymbol();
      if (symbol.isMethodSymbol() && mit.methodSelect().is(Tree.Kind.IDENTIFIER) && isInherited(symbol) && outerClassHasMethodWithSameName(symbol)) {
        String methodName = ((IdentifierTree) mit.methodSelect()).name();
        addIssue(tree, "Prefix this call to \"" + methodName + "\" with \"super.\".");
      }
      super.visitMethodInvocation(tree);
    }

    private boolean isInherited(Symbol symbol) {
      Type methodOwnerType = (Type) symbol.owner().type();
      Type innerType = classSymbol.getType();
      return !symbol.isStatic() && new Types().isSubtype(innerType, methodOwnerType)
        && !classSymbol.owner().getType().equals(methodOwnerType) && !innerType.equals(methodOwnerType);
    }

    private boolean outerClassHasMethodWithSameName(Symbol symbol) {
      return !((org.sonar.java.resolve.Symbol.TypeSymbol) classSymbol.owner()).members().lookup(symbol.name()).isEmpty();
    }

  }
}
