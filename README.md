### **Change Log**  
| Version | Changes | Date |  
|---|---|---|  
| V1.1 | Enabled configuration of sample rate, bit depth, and other parameters | 2019.12.4 |  
| V1.0 | Initial version | 2019.11.27 |  

## **Overview**  
This demo allows access to the microphone array’s **4-channel raw data** and **2-channel reference signal**.  

## **How to Obtain Microphone Array Access Permission**  
### **1. Add Metadata**  
In the `AndroidManifest.xml` file, within the `<application>` tag, add the following code to request microphone array access:  
```xml
<meta-data android:name="ubt-master-app" android:value="third_part_speechservice"/>
```

### **2. Restart the Robot After Installation**  
Each time the robot boots up, it checks whether the installed APK contains the `third_part_speechservice` tag.  
- If the tag exists, the system will **disable** the built-in `SpeechService`, granting microphone array access to the installed APK.  
- Once the built-in `SpeechService` is disabled, the user will no longer be able to access the **Tencent Dingdang voice service**.  

---

## **Releasing Microphone Array Access**  
### **1. Uninstall the App or Remove the `<meta-data>` Tag**  
### **2. Restart the Robot**  
After restarting, the system’s built-in `SpeechService` will regain microphone array access, allowing the user to continue using **Tencent Dingdang voice service**.  

---

## **API Description**  
**Key Class:** `MicArrayUtils`  
### **Parameter Explanation**  
```java
/***
 * @param context
 * @param sampleRates: Sample rate
 * @param bits: Bit depth
 * @param periodCount: Number of samples before triggering data callback
 *                     See {@link DataCallback#onAudioData(byte[])}
 */
```