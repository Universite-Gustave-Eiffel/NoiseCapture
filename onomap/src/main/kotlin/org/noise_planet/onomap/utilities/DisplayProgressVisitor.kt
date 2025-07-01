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

import org.h2gis.api.ProgressVisitor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

class DisplayProgressVisitor(subprocessSize: Long, logProgression: Boolean, minimumSecondsBetweenPrint: Double,
                             val logger: Logger = LoggerFactory.getLogger(DisplayProgressVisitor::class.java)) :
  DefaultProgressVisitor(subprocessSize.toInt(), null) {
  val propertyChangeSupport = PropertyChangeSupport(this)
  var canceled = false
  var logProgression = false
  var lastLoggedProgression = ""
  var minimumSecondsBetweenPrint = 1.0
  var lastPrint: Long = 0

  /**
   * Create the RootProgressVisitor constructor
   * @param subprocessSize
   * @param logProgression
   * @param minimumSecondsBetweenPrint
   */
  init {
    this.logProgression = logProgression
    this.minimumSecondsBetweenPrint = minimumSecondsBetweenPrint
  }

  override fun removePropertyChangeListener(propertyChangeListener: PropertyChangeListener?) {
    propertyChangeSupport.removePropertyChangeListener(propertyChangeListener)
  }

  override fun addPropertyChangeListener(s: String?, propertyChangeListener: PropertyChangeListener?) {
    propertyChangeSupport.addPropertyChangeListener(s, propertyChangeListener)
  }

  /**
   *
   * @param incProg
   */
  @Synchronized
  override fun pushProgression(incProg: Double) {
    val oldProgress = progression
    super.pushProgression(incProg)
    val newProgress = progression
    propertyChangeSupport.firePropertyChange("PROGRESS", oldProgress, newProgress)
    if (logProgression) {
      val newLogProgress = String.format("%.2f %%", newProgress * 100)
      if (newLogProgress != lastLoggedProgression) {
        lastLoggedProgression = newLogProgress
        val t = System.currentTimeMillis()
        if ((t - lastPrint) / 1000.0 > minimumSecondsBetweenPrint) {
          logger.info(newLogProgress)
          lastPrint = t
        }
      }
    }
  }

  /**
   * check if the property is canceled
   * @return a boolen
   */
  override fun isCanceled(): Boolean {
    return canceled
  }

  /**
   * Allow to cancel the property of ProgressVisitor
   */
  override fun cancel() {
    canceled = true
    propertyChangeSupport.firePropertyChange(ProgressVisitor.PROPERTY_CANCELED, false, true)
  }
}
