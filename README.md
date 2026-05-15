# Device State Monitor Multi-Hub — User Guide

**App version:** 1.43  
**Applies to:** Hubitat Elevation, same-LAN multi-hub deployments

---

## Overview

Device State Monitor Multi-Hub reports device states across up to three Hubitat hubs from a single app page. It provides four live tables:

- **ON Devices** — switch-capable devices currently reporting ON
- **OFF Devices** — switch-capable devices currently reporting OFF
- **Unknown State** — switch-capable devices reporting neither ON nor OFF
- **Health / Activity Monitor** — any device (switch or otherwise) that is OFFLINE, INACTIVE, NOT PRESENT, or whose last activity exceeds a configurable time threshold

Device names are clickable links to their hub's device edit page. When Maker API credentials are configured, the State cell in the ON and OFF tables is clickable to toggle the device without leaving the page.

---

## Setting Up Maker API on a Remote Hub

Hubs #2 and #3 are queried via Hubitat's built-in **Maker API** app. Perform these steps on each remote hub before configuring it in Device State Monitor.

### Step 1 — Install Maker API on the remote hub

1. On the remote hub, go to **Apps** → **+ Add Built-In App**.
2. Find **Maker API** and install it.
3. Open the Maker API app. Under **Allow Access to Devices**, select every device you want to be reachable from Device State Monitor — this includes devices you want to monitor for ON/OFF state and any you want in the Health/Activity table. Only devices selected here will be visible to the app.
4. Note the **App ID** shown in the Maker API page heading (e.g. *Maker API — App # 42*). You will need this number.
5. Copy the **Access Token** shown on the same page.
6. Note the hub's **local IP address** (visible in Hubitat's **Settings → Hub Details**, or in your router's DHCP table).

### Step 2 — Test connectivity (optional but recommended)

From a browser on the same LAN, visit:

```
http://<hub-ip>/apps/api/<app-id>/devices?access_token=<token>
```

You should see a JSON list of your selected devices. If the page loads, the credentials are correct.

### Step 3 — Enter credentials in Device State Monitor

Open the Hub #2 (or #3) section in Device State Monitor, enable the hub, expand **Show / Edit Connection Settings**, and enter the IP address, App ID, and Access Token you noted above. Then choose **⟳ Load / Reload Device List** from the Actions dropdown to fetch the device list.

---

## Page Layout

The app page is divided into two zones:

**Top — live report area**
- **Refresh Table** button — re-queries all hubs immediately and redraws all tables
- The four report tables (ON, OFF, Unknown, Health/Activity)
- *Last run* timestamp

**Bottom — collapsed configuration sections**
- Hub #1, Hub #2, Hub #3 settings (each collapsible)
- Sort & Display Options (sort defaults, Hide Columns, filtering, display preferences)
- Notes / User Guide (condensed in-app reference)

Configuration sections are hidden by default once set up, so the report tables are the first thing you see on every visit.

---

## Hub #1 — Local Hub

Hub #1 is the hub running Device State Monitor. Its devices are accessed directly — no network calls are required.

| Control | Purpose |
|---|---|
| **Friendly label** | Name shown in the Hub column of all tables. Defaults to the hub's location name. |
| **Select ON-monitored devices** | Devices that appear in the ON table when their switch state is on. Accepts any switch-capable device. |
| **Select OFF-monitored devices** | Devices that appear in the OFF table when their switch state is off. A device may be selected in both lists. |
| **Toggle Command & Health Monitor Settings** | Toggle to reveal Maker API credentials for Hub #1. These are used for two purposes: (1) clickable State cells in the ON/OFF tables and embedded toggle buttons in the Unknown table; (2) the Load / Select All / Clear All actions in the health device picker. If left blank, State cells are non-interactive and health selection reverts to a manual capability picker. |
| **Hub #1 Health Device List Actions** | Appears once Maker API credentials are entered. Choose **⟳ Load / Reload** to fetch the device list, then **✓ Select All** or **✗ Clear** to bulk-manage health selections. |
| **Select health/activity-monitored devices** | The capability picker — always visible. Devices selected here appear in the Health/Activity table when flagged. Disabled devices are automatically excluded. |

---

## Hubs #2 and #3 — Remote Hubs

Remote hubs are queried via their Maker API on every Refresh.

### Enabling a remote hub

Toggle **Enable Hub #2?** (or #3) to on. The connection settings expand automatically.

### Connection Settings

| Control | Purpose |
|---|---|
| **Show / Edit Connection Settings** | Toggle to reveal or hide the IP, App ID, and Token fields. Collapse it once configured to keep the section tidy. |
| **Hub IP address** | Local LAN IP of the remote hub (e.g. 192.168.1.100). |
| **Maker API app ID** | The number shown in the Maker API app heading on the remote hub. |
| **Maker API access token** | The token from the Maker API app on the remote hub. |
| **Last load status** | Shown in green (OK) or red (error) after a Load/Reload action. Displays counts of switch devices loaded, enabled devices, and disabled devices detected. |

### Actions Dropdown

All bulk device management for remote hubs is done through the **Actions** dropdown. Each selection takes effect immediately on the next page render.

| Action | Effect |
|---|---|
| **⟳ Load / Reload Device List** | Fetches the current device list from the remote hub's Maker API. Must be run before device pickers appear. Re-run whenever devices are added to or removed from the Maker API app on the remote hub. |
| **✓ Select All ON-monitored devices** | Selects all switch-capable devices for ON monitoring. |
| **✗ Clear ON-monitored devices** | Removes all ON monitoring selections. |
| **✓ Select All OFF-monitored devices** | Selects all switch-capable devices for OFF monitoring. |
| **✗ Clear OFF-monitored devices** | Removes all OFF monitoring selections. |
| **✓ Select All health-monitored devices** | Selects all devices (switch and non-switch) for health/activity monitoring. |
| **✗ Clear health-monitored devices** | Removes all health monitoring selections. |

### Device Pickers

After loading, three pickers appear:

**Switch Device Selection** — shows only switch-capable devices. Use the **Filter by name or room** field to narrow a long list. The ON-monitored and OFF-monitored pickers each show the count of devices available in the loaded list.

**Health / Activity device selector** — shows all devices exposed to the Maker API, not just switch-capable ones, so you can monitor sensors, remotes, and other non-switch devices for activity.

### Disabled Devices on Remote Hubs

The Health/Activity table makes a best-effort attempt to exclude disabled devices automatically: on every Refresh it cross-checks selected device IDs against the hub's live device list and skips any ID that no longer appears there (disabled devices typically drop off that list). IDs flagged as disabled during the last Load/Reload are also cached and excluded at refresh time.

This filtering covers most cases, but is not guaranteed — the Maker API does not expose disabled state reliably in all Hubitat versions. If a disabled device still appears in the Health/Activity table, the fallback remedies in order of preference are:

1. **Deselect it** from the health device picker — the cleanest ongoing solution.
2. **Enter its device ID** in the **Manually excluded health-monitor device IDs** field — the device is then permanently excluded from the health table regardless of its state. The device ID is the number in its edit URL: `/device/edit/169`.

Note that the exclusion field applies only to the Health/Activity table, not the ON/OFF/Unknown tables.

---

## Report Tables

### ON Devices Table

Lists every monitored device currently reporting switch state **on**. Devices also selected in the OFF-monitored list are highlighted with a gold star (★) and orange-colored name, indicating they are being watched in both directions.

When Maker API credentials are configured, the State cell (showing **ON**) is clickable. Click it to send an **off** command; the cell updates in-place without a full page refresh.

### OFF Devices Table

Lists every monitored device currently reporting switch state **off**. Same dual-monitoring highlight applies.

Click the State cell (showing **OFF**) to send an **on** command.

### Unknown State Table

Lists monitored devices reporting a switch state other than on or off (e.g. null, initializing, or an unrecognized value). Can be hidden in Sort & Display Options. When Maker API credentials are configured, the State cell contains **→ ON** and **→ OFF** mini-buttons to send either command.

### Health / Activity Monitor Table

Lists any health-monitored device that meets one or more of:

| HE Status | Meaning |
|---|---|
| **OFFLINE** | Hub reports device is offline |
| **INACTIVE** | Hub reports device is inactive |
| **NOT PRESENT** | Hub reports device is not present (typically for presence sensors) |
| **HEALTH OFFLINE** | Device's `healthStatus` attribute reports offline (shown when HE status itself is absent) |
| **Late Activity (>Xh)** | Last recorded device activity is older than the configured threshold |

The **Issue** column may contain multiple reasons separated by commas if more than one condition applies.

For child devices (e.g. individual endpoints of a USB switch hub), last activity is resolved from the parent device if the child has no activity record of its own.

---

## Clickable State Cells

State cells are interactive in the ON, OFF, and Unknown tables when Maker API credentials are configured for the relevant hub. They work as follows:

- **ON / OFF tables:** the entire State cell is a click target. Hover over it to see a subtle highlight; the cursor changes to a pointer. Click to send the opposite command.
- **Unknown table:** the State cell contains **→ ON** and **→ OFF** mini-buttons since neither direction can be inferred.
- The command is sent via the Maker API using `fetch()` — the page does not reload.
- The cell (or button) shows **…** while the command is in flight.
- On success, the State cell's label and color update immediately (optimistic update).
- On failure (network error or non-200 HTTP status), a brief error indicator appears and the original state is restored.

State cells are not interactive in the Health/Activity table — that table is for monitoring only.

---

## Sort & Display Options

Expand the **Sort & Display Options** section at the bottom of the page to configure default sort behaviour, filtering, and display preferences.

### App Name

A **Rename this app** field appears at the top of the section. Enter a custom label to distinguish this instance from others (the label is shown in the Hubitat Apps list).

### Per-Table Sort Settings

Each table has independent **Sort by** and **Order** controls. The sort applied here is the default when the page first loads. Click any column header in a table to re-sort interactively; this does not change the saved default.

**ON / OFF tables:** sortable by Device Name, Room, or Hub.  
**Unknown State table:** same columns.  
**Health / Activity table:** sortable by Device Name, Room, Hub, HE Status, or Last Activity.

### Show/Hide Tables

| Control | Effect |
|---|---|
| **Show Unknown State table?** | Hides or shows the Unknown table. Default: on. |
| **Show Health/Activity Monitor table?** | Hides or shows the Health table. Default: on. |

### Activity Threshold

**Flag devices with last activity more than X hours ago** — default 24 hours. Any health-monitored device whose most recent event is older than this value is flagged as Late Activity in the Health table. Set to a larger value (e.g. 72 hours) to reduce noise for devices that naturally report infrequently.

### Filtering Options

| Control | Effect |
|---|---|
| **Exclude virtual devices** | Omits virtual devices from all four tables. "Virtual" is identified by driver type name containing "virtual", or device name starting with "VD " (a naming convention for virtual devices). |
| **Exclude devices in the "System" room** | Omits devices assigned to the Hubitat room named "System" from all four tables. |

### Display Options

| Control | Effect |
|---|---|
| **Show extra details in section headers?** | Appends monitored device counts to each hub section header (e.g. "Hub #3 – Office — 15 ON / 15 OFF / 47 Health monitored"). |
| **Enable debug logging?** | Writes detailed log entries to the Hubitat log for each device checked during a refresh. Useful for diagnosing missing or incorrect data. Disable when not troubleshooting — it generates a large number of log entries for hubs with many selected devices. |

### Hide Columns

Three toggle buttons at the bottom of Sort & Display Options control column visibility across all tables:

| Button | Columns hidden |
|---|---|
| **Room** | Room column in the ON, OFF, Unknown, and Health tables |
| **Hub** | Hub column in the same four tables |
| **Last Activity** | Last Activity column in the Health / Activity table only (button appears only when the Health table is enabled) |

Click a button to hide the column; click again to show it. The button text is struck through while the column is hidden. Visibility choices are saved in the browser's local storage and restored automatically on every subsequent page load — no Refresh or Save required.

Hiding the Room, Hub, and Last Activity columns is particularly useful on narrow screens (e.g. a phone in portrait orientation), where it frees significant horizontal space for the Device Name, HE Status, and Issue columns.

### Notes / User Guide

A collapsible **Notes / User Guide** section appears below Sort & Display Options and contains a condensed version of this guide for quick in-app reference.

---

## Typical First-Time Setup Sequence

1. Install the app on Hub #1 via **Apps → + Add User App**.
2. Open the app. The four report tables appear (empty) and configuration sections are below.
3. Expand **Hub #1**. Set a friendly label. Select devices for ON, OFF, and health monitoring.
4. To enable clickable State cells and health Select All/Clear All on Hub #1: toggle **Show / Edit Toggle Command & Health Monitor Settings**, enter Maker API credentials for Hub #1, then use the **Hub #1 Health Device List Actions** dropdown to Load and Select All.
5. For each remote hub: expand its section, toggle **Enable**, expand **Connection Settings**, enter IP/App ID/Token, collapse Connection Settings, then choose **⟳ Load / Reload Device List** from the Actions dropdown. After loading, use Select All actions and adjust individual selections as needed.
6. Expand **Sort & Display Options**. Set activity threshold, sort preferences, and filtering options. Use **Hide Columns** to hide Room, Hub, and/or Last Activity columns if desired (handy on mobile).
7. Click **Refresh Table** at the top of the page to run the first full report.
8. Click **Done** to save.

---

## Maintenance

**Adding devices to a remote hub's report:** Add the device in the remote hub's Maker API app, then run **⟳ Load / Reload** from the Actions dropdown. The new device will appear in the pickers.

**Removing a device from monitoring:** Deselect it in the appropriate picker.

**Hub goes offline:** The report shows a red warning banner for that hub and omits its devices from the tables. The remaining hubs still report normally.

**Refresh cadence:** The tables are only updated when you click **Refresh Table** or revisit the app page. The app does not poll on a schedule.
