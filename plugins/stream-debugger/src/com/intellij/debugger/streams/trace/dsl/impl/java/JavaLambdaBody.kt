/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.LambdaBody
import com.intellij.debugger.streams.trace.dsl.StatementFactory

/**
 * @author Vitaliy.Bibaev
 */
class JavaLambdaBody(statementFactory: StatementFactory, override val lambdaArg: Expression) : JavaCodeBlock(statementFactory), LambdaBody {
  override fun toCode(indent: Int): String = if (isExpression()) getStatements().first().toCode() else super.toCode(indent)

  fun isExpression(): Boolean = size == 1

  override fun doReturn(expression: Expression) {
    if (size == 0) {
      addStatement(expression)
    }
    else {
      super.doReturn(expression)
    }
  }
}