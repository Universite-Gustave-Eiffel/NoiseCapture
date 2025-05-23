/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */

package org.noise_planet.onomap.utilities

import com.google.common.util.concurrent.AtomicDouble
import org.h2gis.api.ProgressVisitor
import java.beans.PropertyChangeListener

open class DefaultProgressVisitor(var subprocessSize: Int, var parentProcess: DefaultProgressVisitor?) :
    ProgressVisitor {
  var subprocessDone = AtomicDouble()

  /**
   *
   * @return an instance of the interface ProgressVisitor
   */
  override fun subProcess(i: Int): ProgressVisitor {
      return DefaultProgressVisitor(i, this)
  }

    override fun endStep() {
        pushProgression(1.0)
    }

    open fun pushProgression(incProg: Double) {
        subprocessDone.set((subprocessDone.get() + incProg).coerceAtMost(subprocessSize.toDouble()))
        parentProcess?.pushProgression(incProg / subprocessSize)
    }

    override fun setStep(i: Int) {
      pushProgression(i - subprocessDone.get())
    }

    override fun getStepCount(): Int {
        return subprocessSize
    }

    override fun endOfProgress() {
    }

    override fun getProgression(): Double {
      return parentProcess?.progression ?: (subprocessDone.get() / subprocessSize)
    }

    /**
     * check if the process is cancel or not
     * @return a boolean
     */
    override fun isCanceled(): Boolean {
        return parentProcess != null && parentProcess!!.isCanceled
    }

    /**
     * allow to cancel a process
     */
    override fun cancel() {
        if (parentProcess != null) {
            parentProcess!!.cancel()
        }
    }

    override fun addPropertyChangeListener(s: String?, propertyChangeListener: PropertyChangeListener?) {
        if (parentProcess != null) {
            parentProcess!!.addPropertyChangeListener(s, propertyChangeListener)
        }
    }

    override fun removePropertyChangeListener(propertyChangeListener: PropertyChangeListener?) {
        if (parentProcess != null) {
            parentProcess!!.removePropertyChangeListener(propertyChangeListener)
        }
    }
}
