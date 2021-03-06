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
package com.jetbrains.python.sdk.add

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.ui.JBUI
import com.jetbrains.python.sdk.PreferredSdkComparator
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.detectVirtualEnvs
import com.jetbrains.python.sdk.isAssociatedWithProject
import icons.PythonIcons
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author vlan
 */
class PyAddSdkDialog(private val project: Project?,
                     private val existingSdks: List<Sdk>,
                     private val newProjectPath: String?) : DialogWrapper(project) {

  private var selectedPanel: PyAddSdkPanel? = null
  private var panels: List<PyAddSdkPanel> = emptyList()

  init {
    init()
    title = "Add Local Python Interpreter"
  }

  override fun createCenterPanel(): JComponent {
    val sdks = existingSdks
      .filter { it.sdkType is PythonSdkType && !PythonSdkType.isInvalid(it) }
      .sortedWith(PreferredSdkComparator())
    return createCardSplitter(listOf(createVirtualEnvPanel(project, sdks, newProjectPath),
                                     PyAddSystemWideInterpreterPanel(existingSdks),
                                     createAnacondaPanel()))
  }

  override fun postponeValidation() = false

  override fun doValidateAll(): List<ValidationInfo> = selectedPanel?.validateAll() ?: emptyList()

  fun getOrCreateSdk(): Sdk? = selectedPanel?.getOrCreateSdk()

  private fun createCardSplitter(panels: List<PyAddSdkPanel>): Splitter {
    this.panels = panels
    return Splitter(false, 0.25f).apply {
      val cardLayout = CardLayout()
      val cardPanel = JPanel(cardLayout).apply {
        preferredSize = JBUI.size(640, 480)
        for (panel in panels) {
          add(panel, panel.panelName)
        }
      }
      val cardsList = JBList(panels).apply {
        val descriptor = object : ListItemDescriptorAdapter<PyAddSdkPanel>() {
          override fun getTextFor(value: PyAddSdkPanel) = StringUtil.toTitleCase(value.panelName)
          override fun getIconFor(value: PyAddSdkPanel) = value.icon
        }
        cellRenderer = object : GroupedItemsListRenderer<PyAddSdkPanel>(descriptor) {
          override fun createItemComponent() = super.createItemComponent().apply {
            border = JBUI.Borders.empty(4, 4, 4, 10)
          }
        }
        addListSelectionListener {
          selectedPanel = selectedValue
          cardLayout.show(cardPanel, selectedValue.panelName)
        }
        selectedPanel = panels.getOrNull(0)
        selectedIndex = 0
      }

      firstComponent = cardsList
      secondComponent = cardPanel
    }
  }

  private fun createVirtualEnvPanel(project: Project?,
                                    existingSdks: List<Sdk>,
                                    newProjectPath: String?): PyAddSdkPanel {
    val newVirtualEnvPanel = PyAddNewVirtualEnvPanel(project, existingSdks, newProjectPath)
    val existingVirtualEnvPanel = PyAddExistingVirtualEnvPanel(project, existingSdks, newProjectPath)
    val panels = listOf(newVirtualEnvPanel,
                        existingVirtualEnvPanel)
    val defaultPanel = when {
      detectVirtualEnvs(project, existingSdks).any { it.isAssociatedWithProject(project) } -> existingVirtualEnvPanel
      else -> newVirtualEnvPanel
    }
    return PyAddSdkGroupPanel("Virtual environment", PythonIcons.Python.Virtualenv, panels, defaultPanel)
  }

  private fun createAnacondaPanel(): PyAddSdkPanel {
    val panels = listOf(PyAddNewCondaEnvPanel(project, existingSdks, newProjectPath),
                        PyAddExistingCondaEnvPanel(project, existingSdks, newProjectPath))
    return PyAddSdkGroupPanel("Anaconda", PythonIcons.Python.Anaconda, panels, panels[0])
  }
}
