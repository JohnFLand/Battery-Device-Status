# Battery Device Status — User Guide

**App version:** 1.35  
**Applies to:** Hubitat Elevation, same-LAN multi-hub deployments

---

## Overview

Battery Device Status monitors battery-capable devices across up to three Hubitat hubs from a single app page. It provides up to five selectable report tables:

- **Offline Devices** — battery devices currently reporting OFFLINE, INACTIVE, or NOT PRESENT
- **Low Battery Devices** — devices whose battery level is at or below the configured warning threshold
- **Last Battery Event** — devices that have not reported a battery event within the configured interval
- **Last Event (any type)** — devices that have not reported any event within the configured interval
- **Last Activity** — devices whose last recorded activity exceeds the configured interval

Device names are clickable links to their hub's device edit page. All tables support interactive column sorting by clicking the yellow table headers. Reports can be sent as push notifications (via Pushover or any Hubitat notification device) on a daily schedule or on demand.

---

## Setting Up Maker API on a Remote Hub

Hubs #2 and #3 are queried via Hubitat's built-in **Maker API** app. Perform these steps on each remote hub before configuring it in Battery Device Status.

### Step 1 — Install Maker API on the remote hub

1. On the remote hub, go to **Apps** → **+ Add Built-In App**.
2. Find **Maker API** and install it.
3. Open the Maker API app. Under **Allow Access to Devices**, select every battery device you want Battery Device Status to monitor. Only devices selected here will be visible to the app.
4. Note the **App ID** shown in the Maker API page heading (e.g. *Maker API — App # 42*). You will need this number.
5. Copy the **Access Token** shown on the same page.
6. Note the hub's **local IP address** (visible in Hubitat's **Settings → Hub Details**, or in your router's DHCP table).

### Step 2 — Test connectivity (optional but recommended)

From a browser on the same LAN, visit:

```
http://<hub-ip>/apps/api/<app-id>/devices?access_token=<token>
```

You should see a JSON list of your selected devices. If the page loads, the credentials are correct.

### Step 3 — Enter credentials in Battery Device Status

Open the Hub #2 (or #3) section in Battery Device Status, enable the hub, expand **Show / Edit Connection Settings**, and enter the IP address, App ID, and Access Token you noted above. Then choose **⟳ Load / Reload Device List** from the Actions dropdown to fetch the device list.

---

## Page Layout

The app page is divided into two zones:

**Top — live report area**
- **Refresh Table** button — re-queries all hubs immediately and redraws all tables
- **Send Report Now** button — triggers an immediate notification send to configured devices
- The selected report tables (Offline, Low Battery, Last Battery Event, Last Event, Last Activity)
- **Refresh Table** button repeated at the bottom of the reports for convenience
- *Last run* timestamp and scan time — after each manual refresh, the elapsed time for the full scan is shown next to the timestamp, broken down by report type (e.g. *Scan time: 0:03 [Battery:0.8s, Any:0.6s, Offline:0.1s, Low:0.2s, Activity:1.1s]*). Scan time is not shown after a scheduled run, only after a manual Refresh.

**Bottom — collapsed configuration sections**
- Hub #1, Hub #2, Hub #3 device selection (each collapsible)
- Notification Settings
- Report Type, Schedule & Logging
- Sort Options

Configuration sections are hidden by default once set up, so the report tables are the first thing you see on every visit.

---

## Hub #1 — Local Hub

Hub #1 is the hub running Battery Device Status. Its devices are accessed directly — no network calls are required.

| Control | Purpose |
|---|---|
| **Friendly label** | Name shown in the Hub column of all tables. Defaults to the hub's location name. |
| **Select devices** | Accepts any device with the `battery` capability. Disabled devices are automatically excluded even if selected. |

---

## Hubs #2 and #3 — Remote Hubs

Remote hubs are queried via their Maker API on every Refresh.

### Enabling a remote hub

Toggle **Enable Hub #2?** (or #3) to on. The configuration controls expand automatically.

### Connection Settings

| Control | Purpose |
|---|---|
| **Show / Edit Connection Settings** | Toggle to reveal or hide the IP, App ID, and Token fields. Collapse once configured to keep the section tidy. |
| **Hub IP address** | Local LAN IP of the remote hub (e.g. 192.168.1.100). |
| **Maker API app ID** | The number shown in the Maker API app heading on the remote hub. |
| **Maker API access token** | The token from the Maker API app on the remote hub. |
| **Last load status** | Shown in green (OK) or red (error) after a Load/Reload action. Displays counts of battery devices loaded. |

### Actions Dropdown

All bulk device management for remote hubs is done through the **Actions** dropdown. Each selection takes effect immediately on the next page render.

| Action | Effect |
|---|---|
| **⟳ Load / Reload Device List** | Fetches the current device list from the remote hub's Maker API. Must be run before the device picker appears. Re-run whenever devices are added to or removed from the Maker API app on the remote hub. |
| **✓ Select All battery devices** | Selects all battery-capable devices returned by the load. |
| **✗ Clear all selected devices** | Removes all current selections for that hub. |

### Device Picker

After loading, a device picker appears showing all battery-capable devices returned by the Maker API, along with a count of available devices (e.g. *Battery devices to monitor on Hub #2 (14 available)*).

### Disabled Devices on Remote Hubs

Hubitat's Maker API does not expose disabled state reliably, so disabled devices cannot be filtered automatically on remote hubs. If a disabled device appears in reports, the recommended remedies in order of preference are:

1. **Deselect it** from the device picker — the cleanest ongoing solution.
2. **Enter its device ID** in the **Manually excluded device IDs** field — the device is then permanently excluded from all reports for that hub regardless of its state. The device ID is the number in its edit URL: `/device/edit/169`. Multiple IDs are entered comma-separated.

---

## Report Tables

### Offline Devices

Lists every monitored battery device whose Hubitat status is **OFFLINE**, **INACTIVE**, or **NOT PRESENT**, or whose `healthStatus` attribute reports offline. The summary line shows how many of the total selected devices are currently offline.

Columns: **Battery %**, **Device Name**, **Hub** *(omitted if Hide Hub column is enabled)*

### Low Battery Devices

Lists every monitored device whose current battery level is at or below the **Low battery warning level** threshold. Devices at or below the **Critically low battery level** threshold are displayed in red.

Columns: **Battery %**, **Device Name**, **Hub** *(omitted if Hide Hub column is enabled)*

The summary line reports how many of the total selected devices currently show a low battery level.

### Last Battery Event

Lists devices that have not reported a `battery` attribute event within the configured **Overdue battery event interval**. The app first checks for a `lastBatteryReport` attribute on the device driver; if not present, it searches the event log.

Devices that have never reported a battery event are handled according to the **Include devices with 'Never' battery event but recent activity?** setting — see [Report Type, Schedule & Logging](#report-type-schedule--logging).

Columns: **Last Battery Event Time**, **Battery %**, **Device Name**, **Hub** *(omitted if Hide Hub column is enabled)*

### Last Event (any type)

Lists devices that have not reported any event within the configured **Overdue event interval**. The most recent event name and value are shown in the Event Description column.

Columns: **Last Event Time**, **Battery %**, **Device Name**, **Event Description**, **Hub** *(omitted if Hide Hub column is enabled)*

### Last Activity

Lists devices whose last recorded activity is older than the configured **Overdue activity interval**. Activity timestamps are shown in red. Devices with no activity on record are shown as **[Never]** and sorted to the end of the table.

Columns: **Last Activity Time**, **Battery %**, **Device Name**, **Hub** *(omitted if Hide Hub column is enabled)*

---

## Interactive Table Sorting

All report tables have **yellow column headers** that can be clicked to re-sort the table:

- Click a header once to sort ascending (▲ indicator appears).
- Click the same header again to sort descending (▼ indicator appears).
- **[Never]** entries are always sorted to the end of the table regardless of sort direction.
- Interactive sorting is temporary and does not change the saved default sort. The default sort (used for notifications and on page load) is configured in the [Sort Options](#sort-options) section.

---

## Notification Settings

Battery Device Status sends plain-text reports to any Hubitat notification-capable device (typically Pushover).

| Control | Purpose |
|---|---|
| **Sound notification device(s)** | Receives the **first** report in the notification queue with a sound alert. Select one or more Pushover devices configured with an audible notification tone. |
| **Silent notification device(s)** | Receives all **remaining** reports (second through fifth) with no alert sound. Select one or more Pushover devices configured with a silent/none notification tone. |

When a scheduled or on-demand send is triggered, each selected report generates one notification message. Messages are staggered 5 seconds apart to avoid delivery collisions. Reports with no flagged devices produce no notification.

---

## Report Type, Schedule & Logging

Expand this section to control which reports are generated, when they run, and how thresholds are defined.

### Report Selection

Use the **Select which report tables to generate** picker to choose one or more of: Offline Devices, Low Battery Devices, Last Battery Event, Last Event (any type), Last Activity. Choose **Select All Reports** to enable all five at once.

Only selected reports appear in the on-screen table area and are included in notifications.

### Scheduling

| Control | Purpose |
|---|---|
| **Daily check time** | The time each day the app automatically runs all selected reports and sends notifications. |

### Thresholds

| Control | Default | Purpose |
|---|---|---|
| **Low battery warning level (%)** | 80 | Devices at or below this level appear in the Low Battery table. |
| **Critically low battery level (%)** | 60 | Devices at or below this level are highlighted in red in the Low Battery table. Must be ≤ the low battery warning level. |
| **Overdue battery event interval (hours)** | 24 | Devices whose last battery event is older than this appear in the Last Battery Event table. |
| **Overdue event interval (hours)** | 24 | Devices whose last event (any type) is older than this appear in the Last Event table. |
| **Overdue activity interval (hours)** | 24 | Devices whose last activity is older than this appear in the Last Activity table. This threshold is also used to evaluate "recent activity" for the Never battery event logic below. |

### Display & Behavior Options

| Control | Purpose |
|---|---|
| **Show extra details in section headers?** | Appends current threshold values and settings to collapsed section headers (e.g. "Report Type, Schedule & Logging (Daily Check: 08:00 AM, Overdue Event Interval: 24h…)"). Useful for reviewing settings at a glance without expanding sections. |
| **Include devices with 'Never' battery event but recent activity?** | When enabled, devices that have never reported a battery event are included in the Last Battery Event and Last Event tables even if they have had recent activity. When disabled (default), such devices are excluded to reduce table clutter. |
| **Exclude virtual devices from all reports?** | When enabled, omits virtual devices from all five report tables. "Virtual" is identified by driver type name containing "virtual", or device name starting with "VD " (a naming convention for virtual devices). Applies to both local and remote hub devices. |
| **Hide the Hub column in all report tables?** | When enabled, removes the Hub column from every report table and from plain-text notification output. Recommended when only one hub is being monitored — the column adds no information in that case. Disabled by default. |
| **Enable debug logging?** | Writes detailed log entries to the Hubitat log for each device evaluated during a refresh. Useful for diagnosing missing or incorrect data. Disable when not troubleshooting — it generates a large number of log entries for hubs with many devices. |

---

## Sort Options

Expand the **Sort Options** section to set the default sort order for each report table. These settings also control the sort order used in notification messages.

> **Note:** Click any table header in the live report area to re-sort interactively. This is temporary and does not change the saved default here.

Each table has independent **Sort by** and **Order** (Ascending / Descending) controls.

| Table | Sortable columns |
|---|---|
| **Offline Devices** | Device Name |
| **Low Battery Devices** | Battery %, Device Name |
| **Last Battery Event** | Last Battery Event Time, Device Name |
| **Last Event (any type)** | Last Event Time, Device Name, Event Description |
| **Last Activity** | Last Activity Time, Device Name, Battery % |

For the Low Battery table, when sorted by Battery %, devices at the same battery level are secondarily sorted by Device Name.

---

## Typical First-Time Setup Sequence

1. Install the app on Hub #1 via **Apps → + Add User App**.
2. Open the app. The report area appears (empty) and configuration sections are below.
3. Expand **Hub #1**. Set a friendly label and select all battery devices you want to monitor.
4. For each remote hub: expand its section, toggle **Enable**, expand **Connection Settings**, enter IP / App ID / Token, collapse Connection Settings, then choose **⟳ Load / Reload Device List** from the Actions dropdown. After loading, use **✓ Select All** and deselect any devices you do not want monitored.
5. Expand **Notification Settings**. Select your sound Pushover device(s) for the first report and your silent Pushover device(s) for subsequent reports.
6. Expand **Report Type, Schedule & Logging**. Select the reports you want, set the daily check time, adjust thresholds to match your environment, and configure display options.
7. Expand **Sort Options** and set default sort preferences for each table.
8. Click **Refresh Table** at the top of the page to run the first full report.
9. Click **Send Report Now** to verify that notifications are delivered correctly.
10. Click **Done** to save.

---

## Maintenance

**Adding devices to a remote hub's report:** Add the device in the remote hub's Maker API app, then run **⟳ Load / Reload** from the Actions dropdown. The new device will appear in the picker.

**Removing a device from monitoring:** Deselect it in the appropriate device picker.

**Excluding a persistent disabled/unwanted device on a remote hub:** Enter its device ID (from its edit URL) in the **Manually excluded device IDs** field for that hub. It will no longer appear in any report.

**Hub goes offline:** The affected hub's devices are omitted from all tables for that refresh. The remaining hubs still report normally.

**Refresh cadence:** Tables update only when you click **Refresh Table**, click **Send Report Now**, or the daily scheduled check runs. The app does not poll continuously.

**Adjusting thresholds to reduce noise:** If devices that report infrequently (e.g. door sensors in rarely-used areas) generate unwanted Last Battery Event or Last Activity entries, increase the corresponding interval threshold (e.g. from 24 hours to 72 hours) in the Report Type, Schedule & Logging section.
