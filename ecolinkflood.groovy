/**
 *  Device Type Definition File   
 *
 *  Device Type:        Ecolink Flood Freeze Sensor
 *  File Name:          
 *  Initial Release:    2 / 13 / 2018
 *  @author:            Ecolink
 *  @version:           1.0
 *
 *  Copyright 2014 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Manufacturer ID : 01 4A  : Ecolink
 *  Product Type ID : 00 05  : Flood Freeze
 *  Product ID      : 00 10  : 10
 *
 *  Raw Description : zw:S type:0701 mfr:014A prod:0005 model:0010 ver:10.09 zwv:4.38 lib:06 cc:5E,86,72,5A,73,80,30,71,85,59,84 role:06 ff:8C05 ui:8C05
 *
 * @param none
 *
 * @return none
 */

import groovy.transform.Field

@Field static final Map commandClassVersions = [
   0x20: 1,    // Basic
   0x30: 2,    // SensorBinary
   0x71: 3,    // Notification
   0x80: 1,    // Battery
   0x84: 1,    // Wakeup
   0x9F: 1     // Security S2
]

metadata {
  definition (name: "Ecolink Z-Wave Flood Freeze Sensor", namespace: "Ecolink", author: "Ecolink") {
    capability "Water Sensor"
    capability "Temperature Measurement"
    capability "Battery"
    capability "Tamper Alert"
    capability "Sensor"

    //fingerprint deviceId: "0x0701", inClusters: "0x5E,0x86,0x72,0x73,0x80,0x71,0x85,0x59,0x84,0x30,0x70,0xEF,0x20", model: "0001", prod: "0004"
    fingerprint mfr: "014A", prod: "0005",  model: "0010"
  }
  
  preferences {
    input name: "debug", type: "bool", title: "Enable debug logging", defaultValue: false
  }
}

def updated() {
  log.warn "debug logging is: ${debug == true}"
  if (debug) runIn(1800, "logsOff")  // 1800 seconds = 30 minutes
}

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("debug", [value:"false", type:"bool"])
}

def parse(String description) {
  if (debug) log.debug "Raw command: $description"
  def result = []
  if (description.startsWith("Err")) {
    result = createEvent(descriptionText:description)
  } else {
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
      result = zwaveEvent(cmd)
    } else {
      result = createEvent(value: description, descriptionText: description, isStateChange: false)
    }
  }
  result
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
  if (debug) log.debug "NotificationReport: $cmd"

  final NOTIFICATION_TYPE = hubitat.zwave.commands.notificationv3.NotificationReport
  def result = []
  switch (cmd.notificationType) {
    case NOTIFICATION_TYPE.NOTIFICATION_TYPE_WATER:
      switch (cmd.event) {
        case 0x02:
          result << createEvent(name: "water", value: "wet", descriptionText: "$device.displayName detected water")
        break
        case 0x04:
          result << createEvent(name: "water", value: "dry", descriptionText: "$device.displayName no longer detected water")
        break
        default:
          log.warn "Unknown water notification: ${cmd.event}"
      }
    break
    case NOTIFICATION_TYPE.NOTIFICATION_TYPE_BURGLAR:
      switch (cmd.event) {
        case 0x03:
          result << createEvent(name: "tamper", value: "tampered", descriptionText: "$device.displayName covering was removed", isStateChange: true)
        break
        case 0x00:
          result << createEvent(name: "tamper", value: "secure", descriptionText: "$device.displayName covering was closed", isStateChange: true)
        break
        default:
          log.warn "Unknown tamper notification: ${cmd.event}"
      }
    break
    case NOTIFICATION_TYPE.NotificationV3.NOTIFICATION_TYPE_POWER_MANAGEMENT:
      switch (cmd.event) {
        case 0x0B:
          log.warn "Critical battery, replace immediately"
          result << createEvent(name: "battery", unit: "%", value: 1, descriptionText: "${device.displayName} has a critical battery", isStateChange: true)
        break
        case 0x0A:
          log.warn "${device.displayName} has a low battery"
        break
        default:
          log.warn "Unknown battery notification: ${cmd.notificationType}"
      }
    break
    default:
      log.warn "Unknown notification type: ${cmd.notificationType}"
  }
  result
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
  if (debug) log.debug "SensorBinaryReport v2: $cmd"

  final SENSOR_TYPE = hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport
  def result = []
  switch (cmd.sensorType) {
    case SENSOR_TYPE.SENSOR_TYPE_WATER:
      // ignore, handled by NotificationReport
    break
    case SENSOR_TYPE.SENSOR_TYPE_FREEZE:
      switch (cmd.sensorValue) {
        case 0xFF:
          result << createEvent(name: "water", value: "freezing", descriptionText: "$device.displayName is freezing")
          result << createEvent(name: "temperature", value: getTemperatureScale() == "F" ? 32 : 0, descriptionText: "$device.displayName is freezing")
        break
        case 0x00:
          result << createEvent(name: "water", value: "dry", descriptionText: "$device.displayName is normal")
          result << createEvent(name: "temperature", value: getTemperatureScale() == "F" ? 72 : 22, descriptionText: "$device.displayName is normal")
        default:
          log.warn "Unknown freeze notification: ${cmd.notificationType}"
      }
    break
    default:
      log.warn "Unknown sensor type: ${cmd.sensorType}"
  }
  result
}

def zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd) {
  def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]
  if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
    result << response(zwave.batteryV1.batteryGet())
  } else {
    result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
  }
  result
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  def map = [ name: "battery", unit: "%" ]
  if (cmd.batteryLevel == 0xFF) {
    map.value = 1
    map.descriptionText = "${device.displayName} has a low battery"
    map.isStateChange = true
  } else {
    map.value = cmd.batteryLevel
  }
  state.lastbat = new Date().time
  [createEvent(map), response(zwave.wakeUpV1.wakeUpNoMoreInformation())]
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  def result = []

  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  log.debug "msr: $msr"
  updateDataValue("MSR", msr)

  if (msr == "0086-0002-002D") {  //Set wakeup interval
    result << response(zwave.wakeUpV1.wakeUpIntervalSet(seconds:4*3600, nodeid:zwaveHubNodeId))
  }
  result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
  result
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  log.warn "Unknown Z-Wave Command: $cmd"
}
