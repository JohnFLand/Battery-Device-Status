/*
PURPOSE: Check the state of battery based devices
FEATURES:
    * Shows only devices that report a "battery" capability.
    * Selectively shows devices based on low/critical battery, offline devices, last event (any type), last battery event, and last activity.
    * Configurable reporting interval and reporting time.
    * Output is in a sortable table for ease of reading.
    * Parenthetical details in section headings can be suppressed.
    * Multiple notification devices supported.
    * Plain-text table for notifications (e.g., Pushover, Hubitat)
    * Last run date displayed at bottom of UI.
    * Configurable low battery warning and critical battery level.
    * Users can select one or more reports via checkboxes.
    * Each report has its own Sort By & Order.
	* Tables automatically show when the app is opened.
	* Can now suppress reporting devices in the "Last Battery Event" table if the device simply reports "NEVER" -- reduces table clutter.
	* For Pushover notifications, can now specifiy that the first report goes to a "sound" device and
		other reports go to a "silent" device (must set that notification for that device to be "none").
    * Clickable table headers for visual sorting (notifications use settings-based sort)
    * Yellow headers with default sort indicators
    * Multi-hub support: Hubs #2 and #3 queried via Maker API; remote device names include hub label for identification.
    * Optional toggle to exclude virtual devices from all reports (local and remote hubs).
*/

definition(
    name: "Battery Device Status 1.31",
    namespace: "John Land",
    author: "John Land via ChatGPT",
    description: "Battery Device Status with battery %, offline/low battery reporting, last activity, configurable sort options, multi-hub support",
    installOnOpen: true,
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    def devCount = devs?.findAll { !it.isDisabled() }?.size() ?: 0
    def hub1LabelVal = settings["hub1Label"] ?: (location.name ?: "Hub 1")
    def totalDevCount = devCount +
        ([2, 3].sum { hubNum ->
            settings["hub${hubNum}Enabled"] ? normalizeRemoteSelectionList(settings["hub${hubNum}SelectedDevices"]).size() : 0
        } ?: 0)
    def pageTitle = "Battery Device Status (${totalDevCount} device${totalDevCount == 1 ? '' : 's'} selected)"

    dynamicPage(name: "mainPage", title: pageTitle, uninstall: true, install: true) {

        // ── Reports + Action Buttons (TOP) ───────────────────────────────────
        section(title:"") {
            input "refresh",  "button", title:"Refresh Table"
            input "sendNow",  "button", title:"Send Report Now"
            paragraph handler(sendNotifications=false)
            input "refresh2", "button", title:"Refresh Table"
        }

        // ── Hub #1 – Local Device Selection ──────────────────────────────────
        def devTitle = "Battery Device Selection for Hub #1 – ${hub1LabelVal}"
        devTitle += devCount > 0 ? " — ${devCount} monitored" : " — No devices selected"
        section(hideable:true, hidden:true, title: devTitle) {
            input "hub1Label", "text",
                title: "Friendly label for Hub #1 (shown in section header)",
                defaultValue: (location.name ?: "Hub 1"), required: false, submitOnChange: true
            input "devs", "capability.battery",
                title: "Select devices (disabled devices will be omitted even if selected)",
                submitOnChange:true, multiple:true, required:false
        }

        // ── Hub #2 – Remote ───────────────────────────────────────────────────
        def hub2Enabled    = settings["hub2Enabled"]
        def hub2ActionVal  = settings["hub2Action"]
        def hub2ActionOpen = (hub2ActionVal && hub2ActionVal != "none")
        def hub2LabelVal   = settings["hub2Label"] ?: "Hub 2"
        def hub2SelIds     = normalizeRemoteSelectionList(settings["hub2SelectedDevices"])
        def hub2Title      = "Battery Device Selection for Hub #2 – ${hub2LabelVal}"
        if (hub2Enabled) hub2Title += hub2SelIds.size() > 0 ? " — ${hub2SelIds.size()} monitored" : " — No devices selected"

        section(hideable:true, hidden:!hub2ActionOpen, title:hub2Title) {
            if (hub2ActionOpen) {
                def hub2Stored = state["hub2BatteryDevices"] ?: []
                switch (hub2ActionVal) {
                    case "load":
                        loadRemoteBatteryDeviceList(2, settings["hub2Ip"], settings["hub2AppId"], settings["hub2Token"]); break
                    case "selAll":
                        app.updateSetting("hub2SelectedDevices", [value: hub2Stored.collect { it.id.toString() }, type: "enum"]); break
                    case "unselAll":
                        app.updateSetting("hub2SelectedDevices", [value: [], type: "enum"]); break
                }
                app.updateSetting("hub2Action", [value: "none", type: "enum"])
            }
            input "hub2Enabled", "bool", title:"Enable Hub #2?", defaultValue:false, submitOnChange:true
            if (hub2Enabled) {
                input "hub2Label", "text", title:"Friendly label for Hub #2 (shown after device name in reports)",
                    defaultValue:"Hub 2", required:true, submitOnChange:true
                input "hub2ShowConn", "bool",
                    title:"Show / Edit Connection Settings (IP, App ID, Token)",
                    defaultValue:true, submitOnChange:true
                if (settings["hub2ShowConn"]) {
                    paragraph("<i>Install Maker API on Hub #2, expose all desired battery devices, enter credentials below, then choose <b>Load / Reload Device List</b> from the Action dropdown.</i>")
                    input "hub2Ip",    "text", title:"Hub #2 IP address",             required:true, submitOnChange:false
                    input "hub2AppId", "text", title:"Hub #2 Maker API app ID",       required:true, submitOnChange:false
                    input "hub2Token", "text", title:"Hub #2 Maker API access token", required:true, submitOnChange:false
                } else {
                    def sum = settings["hub2Ip"] ? "Connected to ${settings['hub2Ip']}" : "Not yet configured"
                    paragraph("<small><i>Connection: ${sum}. Toggle above to edit.</i></small>")
                }
                def hub2Status = state["hub2LoadStatus"]
                if (hub2Status) {
                    def c = hub2Status.startsWith("OK") ? "green" : "red"
                    paragraph("<small><i>Last load: <span style='color:${c};font-weight:bold;'>${hub2Status}</span></i></small>")
                }
                input "hub2Action", "enum", title:"Hub #2 Actions", defaultValue:"none",
                    options:["none":"Choose action…",
                             "load":"⟳ Load / Reload Device List from Hub #2",
                             "selAll":"✓ Select All battery devices",
                             "unselAll":"✗ Clear all selected devices"],
                    required:false, submitOnChange:true
                def hub2Opts = buildRemoteBatteryDeviceOptions(2)
                if (hub2Opts == null) {
                    paragraph("<i>Choose <b>Load / Reload Device List</b> above to fetch battery devices from Hub #2.</i>")
                } else if (hub2Opts.size() == 0) {
                    paragraph("<span style='color:red;'>No battery-capable devices returned by Hub #2 Maker API. Check that battery devices are selected in the Maker API app on Hub #2.</span>")
                } else {
                    input "hub2SelectedDevices", "enum",
                        title:"Battery devices to monitor on Hub #2 (${hub2Opts.size()} available)",
                        options:hub2Opts, multiple:true, required:false, submitOnChange:true
                }
                paragraph("<small><i><b>Disabled devices:</b> Hubitat's Maker API does not expose disabled state reliably, so disabled remote devices cannot be filtered automatically. If a disabled device appears in reports, deselect it above or enter its ID below to permanently exclude it. The device ID appears in its edit URL: <code>/device/edit/169</code>. Comma-separated.</i></small>")
                input "hub2ExcludeIds", "text",
                    title:"Hub #2: Manually excluded device IDs (comma-separated)",
                    required:false, submitOnChange:false
            }
        }

        // ── Hub #3 – Remote ───────────────────────────────────────────────────
        def hub3Enabled   = settings["hub3Enabled"]
        def hub3ActionVal = settings["hub3Action"]
        def hub3ActionOpen = (hub3ActionVal && hub3ActionVal != "none")
        def hub3LabelVal  = settings["hub3Label"] ?: "Hub 3"
        def hub3SelIds    = normalizeRemoteSelectionList(settings["hub3SelectedDevices"])
        def hub3Title = "Battery Device Selection for Hub #3 – ${hub3LabelVal}"
        if (hub3Enabled) hub3Title += hub3SelIds.size() > 0 ? " — ${hub3SelIds.size()} monitored" : " — No devices selected"

        section(hideable:true, hidden:!hub3ActionOpen, title:hub3Title) {
            if (hub3ActionOpen) {
                def hub3Stored = state["hub3BatteryDevices"] ?: []
                switch (hub3ActionVal) {
                    case "load":
                        loadRemoteBatteryDeviceList(3, settings["hub3Ip"], settings["hub3AppId"], settings["hub3Token"]); break
                    case "selAll":
                        app.updateSetting("hub3SelectedDevices", [value: hub3Stored.collect { it.id.toString() }, type: "enum"]); break
                    case "unselAll":
                        app.updateSetting("hub3SelectedDevices", [value: [], type: "enum"]); break
                }
                app.updateSetting("hub3Action", [value: "none", type: "enum"])
            }
            input "hub3Enabled", "bool", title:"Enable Hub #3?", defaultValue:false, submitOnChange:true
            if (hub3Enabled) {
                input "hub3Label", "text", title:"Friendly label for Hub #3 (shown after device name in reports)",
                    defaultValue:"Hub 3", required:true, submitOnChange:true
                input "hub3ShowConn", "bool",
                    title:"Show / Edit Connection Settings (IP, App ID, Token)",
                    defaultValue:true, submitOnChange:true
                if (settings["hub3ShowConn"]) {
                    paragraph("<i>Install Maker API on Hub #3, expose all desired battery devices, enter credentials below, then choose <b>Load / Reload Device List</b> from the Action dropdown.</i>")
                    input "hub3Ip",    "text", title:"Hub #3 IP address",             required:true, submitOnChange:false
                    input "hub3AppId", "text", title:"Hub #3 Maker API app ID",       required:true, submitOnChange:false
                    input "hub3Token", "text", title:"Hub #3 Maker API access token", required:true, submitOnChange:false
                } else {
                    def sum = settings["hub3Ip"] ? "Connected to ${settings['hub3Ip']}" : "Not yet configured"
                    paragraph("<small><i>Connection: ${sum}. Toggle above to edit.</i></small>")
                }
                def hub3Status = state["hub3LoadStatus"]
                if (hub3Status) {
                    def c = hub3Status.startsWith("OK") ? "green" : "red"
                    paragraph("<small><i>Last load: <span style='color:${c};font-weight:bold;'>${hub3Status}</span></i></small>")
                }
                input "hub3Action", "enum", title:"Hub #3 Actions", defaultValue:"none",
                    options:["none":"Choose action…",
                             "load":"⟳ Load / Reload Device List from Hub #3",
                             "selAll":"✓ Select All battery devices",
                             "unselAll":"✗ Clear all selected devices"],
                    required:false, submitOnChange:true
                def hub3Opts = buildRemoteBatteryDeviceOptions(3)
                if (hub3Opts == null) {
                    paragraph("<i>Choose <b>Load / Reload Device List</b> above to fetch battery devices from Hub #3.</i>")
                } else if (hub3Opts.size() == 0) {
                    paragraph("<span style='color:red;'>No battery-capable devices returned by Hub #3 Maker API. Check that battery devices are selected in the Maker API app on Hub #3.</span>")
                } else {
                    input "hub3SelectedDevices", "enum",
                        title:"Battery devices to monitor on Hub #3 (${hub3Opts.size()} available)",
                        options:hub3Opts, multiple:true, required:false, submitOnChange:true
                }
                paragraph("<small><i><b>Disabled devices:</b> Hubitat's Maker API does not expose disabled state reliably, so disabled remote devices cannot be filtered automatically. If a disabled device appears in reports, deselect it above or enter its ID below to permanently exclude it. The device ID appears in its edit URL: <code>/device/edit/169</code>. Comma-separated.</i></small>")
                input "hub3ExcludeIds", "text",
                    title:"Hub #3: Manually excluded device IDs (comma-separated)",
                    required:false, submitOnChange:false
            }
        }

        // ── Notification Settings ─────────────────────────────────────────────
		def noticeLabel = noticeSound  ? (noticeSound instanceof Collection  ? noticeSound*.displayName  : [noticeSound.displayName])  : "Select sound notification device(s)"
        def silentLabel = noticeSilent ? (noticeSilent instanceof Collection ? noticeSilent*.displayName : [noticeSilent.displayName]) : "Select silent notification device(s)"
        def noticeTitle = "Notification Settings"

        if (showSectionDetails) noticeTitle += " (${noticeLabel})"
        section(hideable:true, hidden:true, title: noticeTitle) {
            input "noticeSound", "capability.notification",
                title: "Select Pushover device(s) for sound notifications (first report)",
                submitOnChange:false, multiple:true, required:false

            input "noticeSilent", "capability.notification",
                title: "Select Pushover device(s) for silent notifications (remaining reports)",
                submitOnChange:false, multiple:true, required:false
        }

        // ── Report Type, Schedule & Logging ───────────────────────────────────
		def batteryIntervalLabel = batteryIntervalHours ?: 24
        def eventIntervalLabel = eventIntervalHours ?: 24
        def activityIntervalLabel = activityIntervalHours ?: 24
        def runTimeLabel = runTime ? timeToday(runTime, location.timeZone).format("hh:mm a") : ""
        def loggingLabel = enableLogging ? "Enabled" : "Disabled"
        def lowBattLabel = lowBatteryLevel ?: 80
        def critBattLabel = criticalBatteryLevel ?: 60

        def settingsTitle = "Report Type, Schedule & Logging"
        if (showSectionDetails) {
			settingsTitle += " (Daily Check: ${runTimeLabel}, Overdue Event Interval: ${eventIntervalLabel}h, Overdue Battery Interval: ${batteryIntervalLabel}h, " +
							 "Overdue Activity Interval: ${activityIntervalLabel}h, Battery Low = ${lowBattLabel}%, Battery Critical = ${critBattLabel}%, Debug Logging: ${loggingLabel})"
        }

        section(hideable:true, hidden:true, title: settingsTitle) {
            def reportOptions = [
                "offline":"Offline Devices",
                "low":"Low Battery Devices",
                "battery":"Last Battery Event",
                "any":"Last Event (any type)",
                "activity":"Last Activity"
            ]

            def selected = reportTables ?: []
            if (selected.contains("all")) {
                selected = reportOptions.keySet() as List
                app.updateSetting("reportTables", [value: selected, type: "enum"])
            }

            input "reportTables", "enum",
                title:"Select which report tables to generate",
                options:["all":"Select All Reports"] + reportOptions,
                multiple:true,
                value: selected,
                submitOnChange:true,
                required:false

            input name:"runTime", type:"time", title:"Daily check time", required:true
			input "batteryIntervalHours", "number", title:"Overdue battery event interval in hours (default = 24)", defaultValue:24, required:true
            input "eventIntervalHours", "number", title:"Overdue event interval in hours (default = 24)", defaultValue:24, required:true
			input "activityIntervalHours", "number", title:"Overdue activity interval in hours (default = 24)", defaultValue:24, required:true
            input "lowBatteryLevel", "number", title:"Low battery warning level (%)", defaultValue:80, required:true
            input "criticalBatteryLevel", "number", title:"Critically low battery level (%)", defaultValue:60, required:true
            input "showSectionDetails", "bool", title:"Show extra details in section headers?", defaultValue:true
            input "enableLogging", "bool", title:"Enable debug logging?", defaultValue:false
            input "includeNeverRecent", "bool",
                title:"Include devices with 'Never' battery event but recent activity?",
                description:"If enabled, devices with no battery event but recent activity (within Overdue Activity Interval) will still be shown.",
                defaultValue:false
            input "excludeVirtual", "bool",
                title:"Exclude virtual devices from all reports?",
                defaultValue:false
        }

        // ── Sort Options ───────────────────────────────────────────────────────
        section(hideable:true, hidden:true, title:"Sort Options") {
            paragraph("<i><b>Note:</b> These settings control the default sort order for tables AND the sort order used in notifications. You can also click any table header to re-sort the display temporarily.</i>")
            paragraph("<hr>")

            // Offline
            paragraph("<b>Offline Devices</b>")
            input "sortBy_offline", "enum", title:"Sort by", options:["displayName":"Device Name"], defaultValue:"displayName", submitOnChange:true
            input "sortOrder_offline", "enum", title:"Order", options:["asc":"Ascending","desc":"Descending"], defaultValue:"asc", submitOnChange:true
            paragraph("<hr>")

            // Low
            paragraph("<b>Low Battery Devices</b>")
            input "sortBy_low", "enum", title:"Sort by", options:["level":"Battery %","displayName":"Device Name"], defaultValue:"level", submitOnChange:true
            input "sortOrder_low", "enum", title:"Order", options:["asc":"Ascending","desc":"Descending"], defaultValue:"asc", submitOnChange:true
            paragraph("<hr>")

            // Battery
            paragraph("<b>Last Battery Event</b>")
            input "sortBy_battery", "enum", title:"Sort by", options:["lastStr":"Last Battery Event Time","displayName":"Device Name"], defaultValue:"lastStr", submitOnChange:true
            input "sortOrder_battery", "enum", title:"Order", options:["asc":"Ascending","desc":"Descending"], defaultValue:"desc", submitOnChange:true
            paragraph("<hr>")

            // Any
            paragraph("<b>Last Event (any type)</b>")
            input "sortBy_any", "enum", title:"Sort by", options:["lastStr":"Last Event Time","displayName":"Device Name","lastEventStr":"Event Description"], defaultValue:"lastStr", submitOnChange:true
            input "sortOrder_any", "enum", title:"Order", options:["asc":"Ascending","desc":"Descending"], defaultValue:"desc", submitOnChange:true
            paragraph("<hr>")

            // Activity
            paragraph("<b>Last Activity</b>")
            input "sortBy_activity", "enum", title:"Sort by", options:["lastActivity":"Last Activity Time","displayName":"Device Name","level":"Battery %"], defaultValue:"lastActivity", submitOnChange:true
            input "sortOrder_activity", "enum", title:"Order", options:["asc":"Ascending","desc":"Descending"], defaultValue:"desc", submitOnChange:true
            paragraph("<hr>")
        }
    }
}

// Lifecycle
def installed() { initialize() }
def updated() {
    unschedule()
    unsubscribe()
    initialize()
    if (enableLogging) log.debug "Battery Device Status updated with ${devs?.size() ?: 0} devices"
}

void initialize() {
    unschedule()
    if (enableLogging) log.debug "Battery Device Status initializing ..."
    if (runTime) schedule(runTime, handlerX)
    log.info "Battery Device Status initialized with ${devs?.size() ?: 0} devices"
}

void handlerX() {
    state.lastRun = new Date().format("yyyy-MM-dd hh:mm a", location.timeZone)
    handler(sendNotifications=true)
}

def appButtonHandler(btn) {
    switch (btn) {
        case "refresh":
        case "refresh2":
            if (enableLogging) log.debug "Manual refresh requested"
            handler(sendNotifications=false)
            break
        case "sendNow":
            if (enableLogging) log.debug "Immediate report send requested"
            handler(sendNotifications=true)
            break
        default:
            if (enableLogging) log.debug "Unknown button: ${btn}"
            break
    }
}

String handler(sendNotifications=false) {
    state.lastRun = new Date().format("yyyy-MM-dd hh:mm a", location.timeZone)
    def scanStart = new Date().time

    def htmlOut = ""
    def htmlOrder = ["offline","low","battery","any","activity"]
    def allReportTypes = ["activity","any","battery","low","offline"]
    def selectedReports = (reportTables ?: allReportTypes).findAll { allReportTypes.contains(it) }
    if (!selectedReports) selectedReports = allReportTypes
    def reportLabels = [
        "battery":"Last Battery Event",
        "any":"Last Event (any type)",
        "offline":"Offline Devices",
        "low":"Low Battery Devices",
        "activity":"Last Activity"
    ]

    // --- Generate all reports, recording per-report elapsed time ---
    def reportTimings = [:]
    def results = selectedReports.collectEntries { type ->
        def t0 = new Date().time
        def report = (type == "low") ? generateLowBatteryTable(sendNotifications, type) :
                     (type == "activity") ? generateActivityTable(sendNotifications, type) :
                     generateReport(type, sendNotifications)
        reportTimings[type] = new Date().time - t0
        [(type): [label: reportLabels[type], html: report.html, plain: report.plain]]
    }

    // --- Build HTML for display ---
    htmlOrder.each { type ->
        def res = results[type]
        if (res) htmlOut += "<h4 style='margin-bottom:4px;margin-top:8px;'>${res.label}</h4>${res.html}<br>"
    }
    if (!sendNotifications && state.lastRun) {
        def totalElapsed = new Date().time - scanStart
        def totalMins = (totalElapsed / 60000).toInteger()
        def totalSecs = ((totalElapsed % 60000) / 1000).toInteger()
        def totalStr  = String.format("%d:%02d", totalMins, totalSecs)
        def timingParts = reportTimings.collect { type, ms ->
            def lbl = [battery:"Battery", any:"Any", offline:"Offline", low:"Low", activity:"Activity"][type] ?: type
            def secs   = (ms / 1000).toInteger()
            def tenths = ((ms % 1000) / 100).toInteger()
            "${lbl}:${secs}.${tenths}s"
        }
        def timingDetail = timingParts ? " [${timingParts.join(', ')}]" : ""
        htmlOut += "<br><small><i>Last run: ${state.lastRun} (Scan time: ${totalStr}${timingDetail})</i></small>"
    }
    
    // --- Add JavaScript for table sorting ---
    if (!sendNotifications) {
        htmlOut += """
        <script>
        function sortBatteryTable(tableId, columnIndex) {
            const table = document.getElementById(tableId);
            if (!table) return;
            
            const tbody = table.querySelector('tbody');
            if (!tbody) return;
            
            const rows = Array.from(tbody.querySelectorAll('tr'));
            const headers = table.querySelectorAll('th');
            
            // Initialize sort directions object if it doesn't exist
            if (!window.batteryTableSorts) {
                window.batteryTableSorts = {};
            }
            if (!window.batteryTableSorts[tableId]) {
                window.batteryTableSorts[tableId] = {};
            }
            
            // Determine sort direction
            const currentDirection = window.batteryTableSorts[tableId][columnIndex] || 'asc';
            const newDirection = currentDirection === 'asc' ? 'desc' : 'asc';
            window.batteryTableSorts[tableId][columnIndex] = newDirection;
            
            // Remove sort indicators from all headers
            headers.forEach(header => {
                header.classList.remove('sort-asc', 'sort-desc');
                header.style.position = 'relative';
            });
            
            // Add sort indicator to current header
            headers[columnIndex].classList.add('sort-' + newDirection);
            
            // Sort rows
            rows.sort((a, b) => {
                const aCell = a.querySelectorAll('td')[columnIndex];
                const bCell = b.querySelectorAll('td')[columnIndex];
                let aText = aCell ? aCell.textContent.trim() : '';
                let bText = bCell ? bCell.textContent.trim() : '';
                
                // Handle [Never] specially - always sort to end
                const aNever = aText.includes('[Never]');
                const bNever = bText.includes('[Never]');
                
                if (aNever && !bNever) return 1;
                if (!aNever && bNever) return -1;
                if (aNever && bNever) return 0;
                
                // Remove % signs for battery columns
                aText = aText.replace('%', '').trim();
                bText = bText.replace('%', '').trim();
                
                // Try to parse as numbers for numeric sorting
                const aNum = parseFloat(aText.replace(/[^0-9.-]/g, ''));
                const bNum = parseFloat(bText.replace(/[^0-9.-]/g, ''));
                
                let comparison = 0;
                
                if (!isNaN(aNum) && !isNaN(bNum)) {
                    // Numeric comparison
                    comparison = aNum - bNum;
                } else {
                    // String comparison (case-insensitive)
                    comparison = aText.toLowerCase().localeCompare(bText.toLowerCase());
                }
                
                return newDirection === 'asc' ? comparison : -comparison;
            });
            
            // Re-append sorted rows
            rows.forEach(row => tbody.appendChild(row));
        }
        </script>
        <style>
        .battery-table th {
            cursor: pointer;
            user-select: none;
            position: relative;
            background-color: #FFD700;
            color: #000;
            font-weight: bold;
        }
        .battery-table th:hover {
            background-color: #FFC700;
        }
        .battery-table th.sort-asc::after {
            content: ' ▲';
            font-size: 0.8em;
        }
        .battery-table th.sort-desc::after {
            content: ' ▼';
            font-size: 0.8em;
        }
        </style>
        """
    }

    // --- Notification sending ---
    if (sendNotifications) {
        // Ensure devices are arrays
        def soundDevices = noticeSound ? (noticeSound instanceof Collection ? noticeSound : [noticeSound]) : []
        def silentDevices = noticeSilent ? (noticeSilent instanceof Collection ? noticeSilent : [noticeSilent]) : []

        // Build notification queue
        def queue = []
        allReportTypes.eachWithIndex { type, idx ->
            def res = results[type]
            if (!res?.plain?.trim()) return

            def targets = (idx == 0) ? soundDevices : silentDevices
            if (!targets) return

            targets.each { dev ->
                queue << [deviceId: dev.id, msg: "=== ${res.label} ===\n${res.plain.trim()}"]
            }
        }

        // Schedule each message with a 5-second stagger
        queue.eachWithIndex { item, idx ->
            runIn(idx * 5, "sendDelayedNotification", [
                overwrite: false,
                data: item
            ])
        }
    }

    return htmlOut
}

// --- Helper for asynchronous notification sending ---
void sendDelayedNotification(Map data) {
    def deviceId = data.deviceId
    def msg = data.msg
    if (!deviceId || !msg) return

    // Look up device dynamically
    def device = (noticeSound instanceof Collection ? noticeSound : [noticeSound]).find { it.id == deviceId } ?:
                 (noticeSilent instanceof Collection ? noticeSilent : [noticeSilent]).find { it.id == deviceId }

    if (!device) {
        log.warn "Device not found for ID ${deviceId}"
        return
    }

    try {
        device.deviceNotification(msg)
        if (enableLogging) log.debug "Sent notification to ${device.displayName}"
    } catch (e) {
        log.error "Failed to notify ${device.displayName}: ${e}"
    }
}


// Report Generator (last event/battery/offline/any)
private Map generateReport(String type, boolean noteMode) {
    def sortByVal = settings["sortBy_${type}"] ?: ((type=="offline") ? "displayName" : "lastStr")
    def sortOrderVal = settings["sortOrder_${type}"] ?: "asc"

    def selectedEnabledDevices = (devs ?: []).findAll { !it.isDisabled() }
    if (!selectedEnabledDevices && !hasAnyRemoteHubEnabled()) return [label:type, html:"No battery devices found.", plain:"No battery devices found."]

    // Normalize includeNeverRecent setting to a proper boolean (handles "true"/"false" strings)
    def includeNeverRecentFlag = (settings["includeNeverRecent"]?.toString()?.toLowerCase() == "true")
    def excludeVirt = settings["excludeVirtual"] ?: false
    if (excludeVirt) selectedEnabledDevices = selectedEnabledDevices.findAll {
        !it.typeName?.toLowerCase()?.contains("virtual") && !it.displayName?.startsWith("VD ")
    }

    def rightNow = new Date()

    def reportList = selectedEnabledDevices.collect { dev ->

        def lastEvent = null
        def lastEventDate = null

        if (type == "battery") {
            // Prefer the custom lastBatteryReport attribute if the driver exposes it
            def lastReportStr = dev.currentValue("lastBatteryReport")
            if (lastReportStr) {
                try {
                    lastEventDate = Date.parse("yyyy-MM-dd HH:mm:ss", lastReportStr)
                } catch (e) { /* fall through to event log */ }
            }
            if (!lastEventDate) {
                lastEvent = dev.events(max:500)?.find { it.name == "battery" }
                lastEventDate = lastEvent?.date
            }
        } else if (type == "any") {
            lastEvent = dev.events(max:1)?.getAt(0)
            lastEventDate = lastEvent?.date
        } else if (type == "offline") {
            // Pre-check offline status (cheap) before querying the event log,
            // so dev.events() is only called for the small number of actually-offline devices.
            def devStatusPre = dev.getStatus()?.toUpperCase()
            def isOfflinePre = devStatusPre in ["OFFLINE","INACTIVE","NOT PRESENT"] ||
                               (dev.currentHealthStatus?.toLowerCase() == "offline")
            if (isOfflinePre) {
                lastEvent = dev.events(max:1)?.getAt(0)
                lastEventDate = lastEvent?.date
            }
        }
        // For "offline" type: lastEventDate only fetched for confirmed-offline devices

        def eventDesc = (type == "any" && lastEvent) ? "(Event: ${lastEvent.name} ${lastEvent.value})" : ""

        def fs = '\u2007'
        def batteryLevel = dev.currentBattery != null ? Math.round(dev.currentBattery).toString().padLeft(3, fs) : "N/A".padLeft(3, fs)

        def devStatus = dev.getStatus()?.toUpperCase()
        def isOffline = devStatus in ["OFFLINE","INACTIVE","NOT PRESENT"] ||
                        (dev.currentHealthStatus?.toLowerCase() == "offline")

        def needsNotice = false
        if (type == "offline") {
            needsNotice = isOffline
        } else if (type == "battery") {
            if (lastEventDate != null) {       // overdue battery event
				def batteryThresholdHours = (settings["batteryIntervalHours"] ?: 24) as int
				needsNotice = ((rightNow.time - lastEventDate.time)/60000) > (batteryThresholdHours * 60)
            }
            // if lastEventDate == null -> needsNotice stays false (handled later)
        } else if (type == "any") {            // traditional behavior for "any"
            needsNotice = !lastEventDate || ((rightNow.time - lastEventDate.time)/60000) > ((eventIntervalHours ?: 24)*60)
        }

        String lastStrUI   = lastEventDate ? lastEventDate.format("yyyy-MM-dd hh:mm a", location.timeZone)
                                           : "<span style='color:red;'>[Never]</span>"
        String lastStrNote = lastEventDate ? lastEventDate.format("yyyy-MM-dd hh:mm a", location.timeZone)
                                           : "0000-00-00 00:00 xx"
        def row = [
            device      : dev,
            displayName : dev.displayName,
            linkUrl     : "/device/edit/${dev.id}",
            hubLabel    : (settings["hub1Label"] ?: (location.name ?: "Hub 1")),
            lastDate    : lastEventDate,
            lastStrUI   : lastStrUI,
            lastStrNote : lastStrNote,
            lastEventStr: eventDesc,
            level       : batteryLevel,
            offline     : isOffline,
            needs       : needsNotice,
            lastActivity: null   // resolved lazily below for local devices
        ]

        if (enableLogging) {
            log.debug "DBG-${type?.toUpperCase()}: device='${dev.displayName}' | lastDate=${lastEventDate} | offline=${isOffline} | needs=${needsNotice}"
        }

        return row
	}.findAll { it ->
		// --- OFFLINE report: status-only, never include just because lastDate is null ---
		if (type == "offline") {
			return (it.offline == true)
		}

		// Case 1: device has a valid last event and is overdue (needsNotice)
		if (it.lastDate != null && it.needs) return true

		// Case 2: device has NEVER reported (lastDate == null)
		if (it.lastDate == null) {
			// Resolve lastActivity: use device method for local devices, cached value for remote
			def lastAct = it.device ? it.device.getLastActivity() : it.lastActivity
			def nowMs = new Date().time
			def thresholdMs = ((activityIntervalHours ?: 24) * 60 * 60 * 1000)
			def recentAct = lastAct && ((nowMs - lastAct.time) <= thresholdMs)

			if (includeNeverRecentFlag) {
				return true   // show all [Never] devices (for battery/any reports)
			} else {
				return false  // exclude all [Never] devices
			}
		}

		return false
	}

    // ── Add remote hub devices ────────────────────────────────────────────────
    [2, 3].each { hubNum ->
        if (settings["hub${hubNum}Enabled"]) {
            reportList.addAll(fetchRemoteReportRows(hubNum, type, rightNow, includeNeverRecentFlag))
        }
    }

    // Sorting for generateReport
    def neverList  = reportList.findAll { it.lastDate == null }
    def normalList = reportList.findAll { it.lastDate != null }

    neverList = neverList.sort { (it.device ? it.device.displayName : it.displayName).toLowerCase() }

    normalList = normalList.sort { it ->
        switch(sortByVal) {
            case "displayName": return (it.device ? it.device.displayName : it.displayName).toLowerCase()
            case "lastStr":     return it.lastDate
            case "level":       return (it.level.toString().trim().isNumber() ? it.level.toString().trim().toInteger() : -1)
            case "lastEventStr":return it.lastEventStr?.toLowerCase() ?: ""
            default:            return it.lastDate
        }
    }
    if (sortOrderVal == "desc") normalList = normalList.reverse()

    reportList = neverList + normalList

    // Compute total checked (local + remote)
    def totalChecked = selectedEnabledDevices.size()
    [2, 3].each { hubNum ->
        if (settings["hub${hubNum}Enabled"]) {
            totalChecked += normalizeRemoteSelectionList(settings["hub${hubNum}SelectedDevices"]).size()
        }
    }
    def notReportedCount = reportList.size()

    // Update header text based on report type
    def eventThresholdHours   = (settings["eventIntervalHours"] ?: 24) as int
	def batteryThresholdHours = (settings["batteryIntervalHours"] ?: 24) as int
    def headerText
		
    if (type == "offline") {
        headerText = (notReportedCount == 0) ?
            "No devices report as being \"OFFLINE\" or \"INACTIVE\" or \"NOT PRESENT\".\n\n" :
            "${notReportedCount} of ${totalChecked} selected devices report as being \"OFFLINE\" or \"INACTIVE\" or \"NOT PRESENT\".\n\n"
    } else if (type == "battery") {
		headerText = notReportedCount > 0 ?
			(includeNeverRecentFlag ?
				"${notReportedCount} of ${totalChecked} selected devices did not report a \"last battery event\" within ${batteryThresholdHours}h.\n\n" :
				"${notReportedCount} of ${totalChecked} selected devices did not report a \"last battery event\" within ${batteryThresholdHours}h (table excludes devices with a \"Never\" last battery event).\n\n"
			) :
			"${totalChecked!=1?'All':'The'} ${totalChecked} selected device${totalChecked!=1?'s':''} reported a \"last battery event\" within ${batteryThresholdHours}h."
    } else if (type == "any") {
        headerText = notReportedCount > 0 ?
            "${notReportedCount} of ${totalChecked} selected devices did not report any event within ${eventThresholdHours}h.\n\n" :
            "${totalChecked!=1?'All':'The'} ${totalChecked} selected device${totalChecked!=1?'s':''} reported events within ${eventThresholdHours}h."
    }

    def headerHtml = headerText.replace("\n\n","<br><br>")
    def tableHtml = ""

	if (notReportedCount>0) {
        def tableId = "table_${type}"
        
        // Determine which column gets the default sort indicator
        def sortColIndex = 0
        if (sortByVal == "displayName") sortColIndex = 2
        else if (sortByVal == "lastStr") sortColIndex = 0
        else if (sortByVal == "level") sortColIndex = 1
        else if (sortByVal == "lastEventStr") sortColIndex = 3
        
        def sortClass = (sortOrderVal == "desc") ? "sort-desc" : "sort-asc"
        
        tableHtml = "<table id='${tableId}' class='battery-table' border='1' cellpadding='4' cellspacing='0' style='border-collapse:collapse;width:100%'><thead><tr>"
		def col1Header = (type == "battery") ? "Last Battery Event Time" : "Last Event Time"
		tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 0)' class='${sortColIndex == 0 ? sortClass : ""}' style='width:210px;'>${col1Header}</th>"
        tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 1)' class='${sortColIndex == 1 ? sortClass : ""}' style='width:100px;'>Battery %</th>"
        tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 2)' class='${sortColIndex == 2 ? sortClass : ""}'>Device Name</th>"
        if (type=="any") tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 3)' class='${sortColIndex == 3 ? sortClass : ""}'>Event Description</th>"
        tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", ${type=='any' ? 4 : 3})' style='width:120px;'>Hub</th>"
        tableHtml += "</tr></thead><tbody>"

        reportList.each { it ->
            def devName = it.device ? it.device.displayName : it.displayName
            def devLink = it.device ? "/device/edit/${it.device.id}" : it.linkUrl
            tableHtml += "<tr>"
            tableHtml += "<td>${it.lastStrUI}</td><td>${it.level}%</td><td><a href='${devLink}' target='_blank'>${devName}</a></td>"
            if (type=="any") tableHtml += "<td>${it.lastEventStr}</td>"
            tableHtml += "<td>${it.hubLabel ?: ''}</td>"
            tableHtml += "</tr>"
        }
        tableHtml += "</tbody></table>"
    }

    def rows = []
    if (notReportedCount>0) {
		def col1HeaderPlain = (type == "battery") ? "Last Battery Event Time" : "Last Event Time"
		def header = "${col1HeaderPlain}            Battery %   Device Name"
        if (type=="any") header += "   Event Description"
        header += "          Hub"

        rows << header
        rows << "-" * header.size()

        reportList.each { it ->
            def devName = it.device ? it.device.displayName : it.displayName
            def row = "${it.lastStrNote}   ${it.level.toString()}%        ${devName}"
            if (type=="any") row += "   ${it.lastEventStr}"
            row += "   ${it.hubLabel ?: ''}"
            rows << row
        }
    }

    def plainMsg = headerText + (rows ? "\n" + rows.join("\n") : "")
    return [label:type, html: headerHtml + tableHtml, plain: plainMsg]

}

// Low Battery Table
private Map generateLowBatteryTable(boolean noteMode, String type = "low") {
    def excludeVirt = settings["excludeVirtual"] ?: false
    def selectedEnabledDevices = (devs ?: []).findAll { !it.isDisabled() &&
        !(excludeVirt && (it.typeName?.toLowerCase()?.contains("virtual") || it.displayName?.startsWith("VD ")))
    }

    def lowList = selectedEnabledDevices ? selectedEnabledDevices.collect { dev ->
        def level = dev.currentBattery
        if (level != null && level <= lowBatteryLevel) {
            def critical = (level <= criticalBatteryLevel)
            [device:dev, displayName:dev.displayName, linkUrl:"/device/edit/${dev.id}", hubLabel:(settings["hub1Label"] ?: (location.name ?: "Hub 1")), level:level, critical:critical]
        }
    }.findAll{ it != null } : []

    // ── Add remote hub devices ────────────────────────────────────────────────
    [2, 3].each { hubNum ->
        if (settings["hub${hubNum}Enabled"]) {
            lowList.addAll(fetchRemoteLowBatteryRows(hubNum))
        }
    }

    if (!lowList) return [label:"Low Battery", html:"No low battery devices.", plain:"No low battery devices."]

    def sortByVal = settings["sortBy_low"] ?: "level"
    def sortOrderVal = settings["sortOrder_low"] ?: "asc"

    def normName = { row -> (row?.device?.displayName ?: row?.displayName ?: "").toLowerCase() }

    lowList = lowList.sort { a, b ->
        if (sortByVal == "displayName") {
            // Device name sort (respects asc/desc)
            int c = normName(a) <=> normName(b)
            return (sortOrderVal == "desc") ? -c : c
        }

        // sortByVal == "level" (Battery %)
        int lvlA = (a?.level instanceof Number) ? a.level as int : -1
        int lvlB = (b?.level instanceof Number) ? b.level as int : -1

        if (sortOrderVal == "desc") {
            // Primary: level DESC
            int c = lvlB <=> lvlA
            if (c != 0) return c
            // Secondary: name ASC within each level
            return normName(a) <=> normName(b)
        } else {
            // Primary: level ASC
            int c = lvlA <=> lvlB
            if (c != 0) return c
            // Secondary: name ASC within each level
            return normName(a) <=> normName(b)
        }
    }

    // Compute total checked (local + remote)
    def totalChecked = selectedEnabledDevices.size()
    [2, 3].each { hubNum ->
        if (settings["hub${hubNum}Enabled"]) {
            totalChecked += normalizeRemoteSelectionList(settings["hub${hubNum}SelectedDevices"]).size()
        }
    }
    def lowCount = lowList.size()
    def summaryText = "${lowCount} of ${totalChecked} selected devices report having low battery levels."

    // Determine which column gets the default sort indicator
    def sortColIndex = (sortByVal == "level") ? 0 : 1
    def sortClass = (sortOrderVal == "desc") ? "sort-desc" : "sort-asc"

    def tableId = "table_${type}"
    def tableHtml = "${summaryText}<br><br>"
    tableHtml += "<table id='${tableId}' class='battery-table' style='border-collapse:collapse;width:100%;border:1px solid black;' cellpadding='4' cellspacing='0'><thead><tr>"
    tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 0)' class='${sortColIndex == 0 ? sortClass : ""}' style='border:1px solid black;width:100px;'>Battery %</th>"
    tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 1)' class='${sortColIndex == 1 ? sortClass : ""}' style='border:1px solid black;'>Device Name</th>"
    tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 2)' style='border:1px solid black;width:120px;'>Hub</th>"
    tableHtml += "</tr></thead><tbody>"
    lowList.each { it ->
        def color = it.level <= criticalBatteryLevel ? "red" : "black"
        def devName = it.device ? it.device.displayName : it.displayName
        def devLink = it.device ? "/device/edit/${it.device.id}" : it.linkUrl
        tableHtml += "<tr>"
        tableHtml += "<td style='color:${color};border:1px solid black;'>${it.level}%</td>"
        tableHtml += "<td style='border:1px solid black;'><a href='${devLink}' target='_blank'>${devName}</a></td>"
        tableHtml += "<td style='border:1px solid black;'>${it.hubLabel ?: ''}</td>"
        tableHtml += "</tr>"
    }
    tableHtml += "</tbody></table>"

    def plainRows = lowList.collect { it ->
        def devName = it.device ? it.device.displayName : it.displayName
        "${it.level}%   ${devName}   ${it.hubLabel ?: ''}"
    }
    def plainMsg = "${summaryText}\n\n" + plainRows.join("\n")
    return [label:"Low Battery", html: tableHtml, plain: plainMsg]
}

// Last Activity Table
private Map generateActivityTable(boolean noteMode, String type = "activity") {
    def sortByVal = settings["sortBy_activity"] ?: "lastActivity"
    def sortOrderVal = settings["sortOrder_activity"] ?: "desc"
    def activityThresholdHours = (settings["activityIntervalHours"] ?: 24) as int
    def thresholdMillis = activityThresholdHours * 60 * 60 * 1000
    def excludeVirt = settings["excludeVirtual"] ?: false

    def selectedEnabledDevices = (devs ?: []).findAll { !it.isDisabled() &&
        !(excludeVirt && (it.typeName?.toLowerCase()?.contains("virtual") || it.displayName?.startsWith("VD ")))
    }
    if (!selectedEnabledDevices && !hasAnyRemoteHubEnabled()) return [label:"Last Activity", html:"No battery devices found.", plain:"No battery devices found."]

    def rightNow = new Date()
    def reportList = selectedEnabledDevices ? selectedEnabledDevices.collect { dev ->
        def lastActivity = dev.getLastActivity()
        def overdue = lastActivity ? (rightNow.time - lastActivity.time > thresholdMillis) : true

        String lastStrUI
        String lastStrNote
        if (lastActivity) {
            def formatted = lastActivity.format("yyyy-MM-dd hh:mm a", location.timeZone)
            lastStrUI   = "<span style='color:red;'>${formatted}</span>"
            lastStrNote = formatted
        } else {
            lastStrUI   = "<span style='color:red;'>[Never]</span>"
            lastStrNote = "0000-00-00 00:00 xx"
        }

        def fs = '\u2007'
        def batteryLevel = dev.currentBattery != null ? Math.round(dev.currentBattery).toString().padLeft(3, fs) : "N/A".padLeft(3, fs)

        [device:dev, displayName:dev.displayName, linkUrl:"/device/edit/${dev.id}",
         hubLabel:(settings["hub1Label"] ?: (location.name ?: "Hub 1")),
         lastActivity:lastActivity, lastStrUI:lastStrUI, lastStrNote:lastStrNote, level:batteryLevel, overdue:overdue]
    }.findAll { it.overdue } : []

    // ── Add remote hub devices ────────────────────────────────────────────────
    [2, 3].each { hubNum ->
        if (settings["hub${hubNum}Enabled"]) {
            reportList.addAll(fetchRemoteActivityRows(hubNum, rightNow, thresholdMillis))
        }
    }

	// Sorting for generateActivityTable
	def neverList  = reportList.findAll { it.lastActivity == null }
	def normalList = reportList.findAll { it.lastActivity != null }

	// Sort "Never" group alphabetically by device name (always asc)
	neverList = neverList.sort { (it.device ? it.device.displayName : it.displayName).toLowerCase() }

	// Sort normal group by selected sort key
	normalList = normalList.sort { it ->
		switch(sortByVal) {
			case "displayName": return (it.device ? it.device.displayName : it.displayName).toLowerCase()
			case "lastActivity":return it.lastActivity
			case "level":       return (it.level.toString().trim().isNumber() ? it.level.toString().trim().toInteger() : -1)
			default:            return it.lastActivity
		}
	}
	if (sortOrderVal == "desc") normalList = normalList.reverse()

	// Combine: [Never] group always first
	reportList = neverList + normalList

    // Compute total checked (local + remote)
    def totalChecked = selectedEnabledDevices.size()
    [2, 3].each { hubNum ->
        if (settings["hub${hubNum}Enabled"]) {
            totalChecked += normalizeRemoteSelectionList(settings["hub${hubNum}SelectedDevices"]).size()
        }
    }
    def overdueCount = reportList.size()
    def summaryText = (overdueCount > 0) ?
        "${overdueCount} of ${totalChecked} selected devices have overdue activity." :
    	"${totalChecked!=1?'All':'The'} ${totalChecked} selected device${totalChecked!=1?'s':''} reported activity within ${activityThresholdHours}h."

    // Determine which column gets the default sort indicator
    def sortColIndex = 0
    if (sortByVal == "displayName") sortColIndex = 2
    else if (sortByVal == "lastActivity") sortColIndex = 0
    else if (sortByVal == "level") sortColIndex = 1
    
    def sortClass = (sortOrderVal == "desc") ? "sort-desc" : "sort-asc"

    // Build HTML table
    def tableId = "table_${type}"
    def tableHtml = "${summaryText}<br><br>"
    if (overdueCount > 0) {
        tableHtml += "<table id='${tableId}' class='battery-table' style='border-collapse:collapse;width:100%;border:1px solid black;' cellpadding='4' cellspacing='0'><thead><tr>"
        tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 0)' class='${sortColIndex == 0 ? sortClass : ""}' style='border:1px solid black;width:210px;'>Last Activity</th>"
        tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 1)' class='${sortColIndex == 1 ? sortClass : ""}' style='border:1px solid black;width:100px;'>Battery %</th>"
        tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 2)' class='${sortColIndex == 2 ? sortClass : ""}' style='border:1px solid black;'>Device Name</th>"
        tableHtml += "<th onclick='sortBatteryTable(\"${tableId}\", 3)' style='border:1px solid black;width:120px;'>Hub</th>"
        tableHtml += "</tr></thead><tbody>"
        reportList.each { it ->
            def devName = it.device ? it.device.displayName : it.displayName
            def devLink = it.device ? "/device/edit/${it.device.id}" : it.linkUrl
            tableHtml += "<tr>"
            tableHtml += "<td style='border:1px solid black;'>${it.lastStrUI}</td>"
            tableHtml += "<td style='border:1px solid black;'>${it.level}%</td>"
            tableHtml += "<td style='border:1px solid black;'><a href='${devLink}' target='_blank'>${devName}</a></td>"
            tableHtml += "<td style='border:1px solid black;'>${it.hubLabel ?: ''}</td>"
            tableHtml += "</tr>"
        }
        tableHtml += "</tbody></table>"
    }

    // Plain text version
    def plainRows = []
    if (overdueCount > 0) {
        plainRows = reportList.collect { row ->
            def devName = row.device ? row.device.displayName : row.displayName
            "${row.lastStrNote}   ${row.level}%   ${devName}   ${row.hubLabel ?: ''}"
        }
    }
    def plainMsg = "${summaryText}\n\n" + (plainRows ? plainRows.join("\n") : "")

    return [label:"Last Activity", html: tableHtml, plain: plainMsg]
}

// Shared sort helper to keep "[Never]" grouped first and alphabetically
private List sortWithNeverGrouping(List reportList, String sortByVal, String sortOrderVal, boolean activityMode=false) {
    def sorted = reportList.sort { it ->
        def never = activityMode ? (it.lastActivity == null) : (it.lastDate == null)
        def tier = never ? 0 : 1
        def secondary = never ? (it.device ? it.device.displayName : it.displayName).toLowerCase() : null
        def sortKey
        switch(sortByVal) {
            case "displayName": sortKey = (it.device ? it.device.displayName : it.displayName).toLowerCase(); break
            case "lastActivity":
                sortKey = it.lastActivity ?: new Date(0)
                break
            case "lastStr":
                sortKey = it.lastDate ?: new Date(0)
                break
            case "level":
                sortKey = (it.level.toString().trim().isNumber() ? it.level.toString().trim().toInteger() : -1)
                break
            case "lastEventStr":
                sortKey = it.lastEventStr?.toLowerCase() ?: ""
                break
            default:
                sortKey = activityMode ? (it.lastActivity ?: new Date(0)) : (it.lastDate ?: new Date(0))
        }
        return [tier, secondary, sortKey]
    }
    if (sortOrderVal == "desc") sorted = sorted.reverse()
    return sorted
}


// ─────────────────────────────────────────────────────────────────────────────
// REMOTE HUB HELPERS
// ─────────────────────────────────────────────────────────────────────────────

// Returns true if at least one remote hub is enabled (used to avoid early-return on empty local devs)
private boolean hasAnyRemoteHubEnabled() {
    return (settings["hub2Enabled"] || settings["hub3Enabled"])
}

// Normalise a multi-select setting into a List<String>
private List normalizeRemoteSelectionList(def raw) {
    if (raw instanceof List)       return raw*.toString()
    if (raw instanceof Collection) return raw.collect { it.toString() }
    return raw ? [raw.toString()] : []
}

// Load battery-capable devices from a remote hub via Maker API and cache in state
private void loadRemoteBatteryDeviceList(int hubNum, String ip, String appId, String token) {
    def hubLabel = settings["hub${hubNum}Label"] ?: "Hub ${hubNum}"
    if (!ip || !appId || !token) {
        state["hub${hubNum}BatteryDevices"] = []
        state["hub${hubNum}LoadStatus"] = "Error: missing IP, app ID, or token"
        return
    }
    def uri = "http://${ip}/apps/api/${appId}/devices?access_token=${token}"
    def batteryList = []
    try {
        httpGet([uri:uri, contentType:"application/json", timeout:15]) { resp ->
            if (resp.status != 200) {
                state["hub${hubNum}BatteryDevices"] = []
                state["hub${hubNum}LoadStatus"] = "Error: HTTP ${resp.status}"
                return
            }
            resp.data?.each { dev ->
                def isDisabled = dev.disabled == true || dev.disabled?.toString() == "true" ||
                                 (dev.status ?: "").toString().toUpperCase() == "DISABLED"
                if (!isDisabled) {
                    batteryList << [
                        id  : dev.id?.toString(),
                        name: (dev.label ?: dev.name ?: "Unknown").toString(),
                        room: (dev.room ?: "").toString()
                    ]
                }
            }
        }
        state["hub${hubNum}BatteryDevices"] = batteryList
        state["hub${hubNum}LoadStatus"] = "OK: ${batteryList.size()} battery device${batteryList.size() == 1 ? '' : 's'} loaded"
        log.info "${hubLabel}: Loaded ${batteryList.size()} battery device(s) for monitoring."
    } catch (Exception e) {
        log.error "${hubLabel}: Error loading device list — ${e.message}"
        state["hub${hubNum}BatteryDevices"] = []
        state["hub${hubNum}LoadStatus"] = "Error: ${e.message}"
    }
}

// Build the options map for the device-selector dropdown in mainPage
private Map buildRemoteBatteryDeviceOptions(int hubNum) {
    def stored = state["hub${hubNum}BatteryDevices"]
    if (stored == null) return null          // not yet loaded
    if (!stored)        return [:]           // loaded but empty
    return stored.sort { it.name }.collectEntries { dev ->
        def label = dev.name + (dev.room ? " (${dev.room})" : "")
        ["${dev.id}": label]
    }
}

// ── Fetch rows for generateReport (offline / battery / any) ──────────────────
// Makes one /devices/{id} call per device for attributes+status, then one
// /devices/{id}/events call for event timestamps when the report type needs them.
// Returns pre-filtered rows in the same map format used by generateReport.
private List fetchRemoteReportRows(int hubNum, String type, Date rightNow, boolean includeNeverRecentFlag) {
    def results  = []
    def hubLabel = settings["hub${hubNum}Label"] ?: "Hub ${hubNum}"
    def ip       = settings["hub${hubNum}Ip"]
    def appId    = settings["hub${hubNum}AppId"]
    def token    = settings["hub${hubNum}Token"]

    def selectedIds = normalizeRemoteSelectionList(settings["hub${hubNum}SelectedDevices"])
    def excludeIds  = (settings["hub${hubNum}ExcludeIds"] ?: "").split(",").collect { it.trim() }.findAll { it } as Set

    if (!selectedIds || !ip || !appId || !token) return results

    def batteryThresholdHours  = (settings["batteryIntervalHours"]  ?: 24) as int
    def activityThresholdHours = (settings["activityIntervalHours"] ?: 24) as int
    def fs = '\u2007'

    selectedIds.each { devId ->
        if (excludeIds.contains(devId)) return
        try {
            def displayName  = null
            def batteryLevel = null
            def isOffline    = false
            def devType      = ""
            def hasBattery   = false
            Date lastBatteryReportDate = null
            Date lastActivity          = null

            // ── Step 1: fetch device details ──────────────────────────────────
            httpGet([uri:"http://${ip}/apps/api/${appId}/devices/${devId}?access_token=${token}",
                     contentType:"application/json", timeout:10]) { resp ->
                if (resp.status != 200 || !resp.data) return
                def dev = resp.data

                displayName = (dev.label ?: dev.name ?: "Unknown").toString()
                devType     = (dev.type ?: "").toString().toLowerCase()

                def rawStatus = (dev.status ?: "").toString().toUpperCase()
                isOffline = rawStatus in ["OFFLINE","INACTIVE","NOT PRESENT"]

                def attrs = dev.attributes
                def battAttr = null
                def lbrAttr  = null
                if (attrs instanceof List) {
                    battAttr = attrs.find { a -> a?.name?.toString() == "battery" }
                    lbrAttr  = attrs.find { a -> a?.name?.toString() == "lastBatteryReport" }
                } else if (attrs instanceof Map) {
                    battAttr = [currentValue: attrs["battery"]]
                    lbrAttr  = [currentValue: attrs["lastBatteryReport"]]
                }

                def rawBatt = battAttr?.currentValue
                batteryLevel = (rawBatt != null && rawBatt.toString().isNumber()) ? rawBatt.toDouble() : null
                hasBattery = (battAttr != null)

                def lbrVal = lbrAttr?.currentValue
                if (lbrVal) {
                    try { lastBatteryReportDate = Date.parse("yyyy-MM-dd HH:mm:ss", lbrVal.toString()) } catch (e) {}
                }

                lastActivity = parseRemoteLastActivity(dev.lastActivity, hubLabel, devId)
            }

            if (!displayName) return   // device fetch failed
            if (!hasBattery)  return   // no battery attribute — not a battery device
            def excludeVirt = settings["excludeVirtual"] ?: false
            if (excludeVirt && (devType.contains("virtual") || displayName.startsWith("VD "))) return

            // ── Step 2: fetch events when needed (battery / any types) ────────
            Date lastBatteryEventDate = lastBatteryReportDate
            Date lastAnyEventDate     = null
            def  lastAnyEventName     = ""
            def  lastAnyEventValue    = ""

            if (type in ["battery", "any"]) {
                try {
                    httpGet([uri:"http://${ip}/apps/api/${appId}/devices/${devId}/events?access_token=${token}",
                             contentType:"application/json", timeout:10]) { evResp ->
                        if (evResp.status == 200 && evResp.data instanceof List && evResp.data.size() > 0) {
                            def events = evResp.data
                            // Most-recent event (first in list)
                            lastAnyEventDate  = parseRemoteLastActivity(events[0].date ?: events[0].time, hubLabel, "${devId}-any")
                            lastAnyEventName  = events[0].name?.toString() ?: ""
                            lastAnyEventValue = events[0].value?.toString() ?: ""
                            // Last battery event (if not already from lastBatteryReport attribute)
                            if (lastBatteryEventDate == null) {
                                def battEv = events.find { e -> e.name?.toString() == "battery" }
                                if (battEv) lastBatteryEventDate = parseRemoteLastActivity(battEv.date ?: battEv.time, hubLabel, "${devId}-batt")
                            }
                            // Last activity fallback
                            if (lastActivity == null) lastActivity = lastAnyEventDate
                        }
                    }
                } catch (evEx) {
                    if (enableLogging) log.debug "${hubLabel} device ${devId}: events fetch failed — ${evEx.message}"
                }
            }

            // ── Step 3: determine lastEventDate, eventDesc, needsNotice ───────
            Date lastEventDate = null
            def  eventDesc     = ""
            def  needsNotice   = false

            if (type == "offline") {
                needsNotice = isOffline
            } else if (type == "battery") {
                lastEventDate = lastBatteryEventDate
                if (lastEventDate != null) {
                    needsNotice = ((rightNow.time - lastEventDate.time) / 60000) > (batteryThresholdHours * 60)
                }
            } else if (type == "any") {
                lastEventDate = lastAnyEventDate
                eventDesc = lastAnyEventDate ? "(Event: ${lastAnyEventName} ${lastAnyEventValue})" : ""
                needsNotice = !lastEventDate || ((rightNow.time - lastEventDate.time) / 60000) > ((eventIntervalHours ?: 24) * 60)
            }

            // ── Step 4: apply same include/exclude filter as local devices ────
            def includeRow = false
            if (type == "offline") {
                includeRow = needsNotice
            } else if (lastEventDate != null && needsNotice) {
                includeRow = true
            } else if (lastEventDate == null) {
                includeRow = includeNeverRecentFlag
            }

            if (!includeRow) return

            def levelStr = batteryLevel != null ? Math.round(batteryLevel).toString().padLeft(3, fs) : "N/A".padLeft(3, fs)
            String lastStrUI   = lastEventDate ? lastEventDate.format("yyyy-MM-dd hh:mm a", location.timeZone) : "<span style='color:red;'>[Never]</span>"
            String lastStrNote = lastEventDate ? lastEventDate.format("yyyy-MM-dd hh:mm a", location.timeZone) : "0000-00-00 00:00 xx"

            results << [
                device      : null,
                displayName : displayName,
                hubLabel    : hubLabel,
                linkUrl     : "http://${ip}/device/edit/${devId}",
                lastDate    : lastEventDate,
                lastStrUI   : lastStrUI,
                lastStrNote : lastStrNote,
                lastEventStr: eventDesc,
                level       : levelStr,
                offline     : isOffline,
                needs       : needsNotice,
                lastActivity: lastActivity
            ]

            if (enableLogging) log.debug "DBG-REMOTE-${type?.toUpperCase()}: hub=${hubLabel} device='${displayName}' | lastDate=${lastEventDate} | offline=${isOffline} | needs=${needsNotice}"

        } catch (Exception e) {
            log.warn "${hubLabel} device ${devId}: error in fetchRemoteReportRows — ${e.message}"
        }
    }

    return results
}

// ── Fetch rows for generateLowBatteryTable ────────────────────────────────────
private List fetchRemoteLowBatteryRows(int hubNum) {
    def results  = []
    def hubLabel = settings["hub${hubNum}Label"] ?: "Hub ${hubNum}"
    def ip       = settings["hub${hubNum}Ip"]
    def appId    = settings["hub${hubNum}AppId"]
    def token    = settings["hub${hubNum}Token"]

    def selectedIds = normalizeRemoteSelectionList(settings["hub${hubNum}SelectedDevices"])
    def excludeIds  = (settings["hub${hubNum}ExcludeIds"] ?: "").split(",").collect { it.trim() }.findAll { it } as Set

    if (!selectedIds || !ip || !appId || !token) return results

    selectedIds.each { devId ->
        if (excludeIds.contains(devId)) return
        try {
            httpGet([uri:"http://${ip}/apps/api/${appId}/devices/${devId}?access_token=${token}",
                     contentType:"application/json", timeout:10]) { resp ->
                if (resp.status != 200 || !resp.data) return
                def dev = resp.data

                def displayName = (dev.label ?: dev.name ?: "Unknown").toString()
                def devType    = (dev.type ?: "").toString().toLowerCase()
                def excludeVirt = settings["excludeVirtual"] ?: false
                if (excludeVirt && (devType.contains("virtual") || displayName.startsWith("VD "))) return

                def attrs    = dev.attributes
                def battAttr = null
                if (attrs instanceof List) {
                    battAttr = attrs.find { a -> a?.name?.toString() == "battery" }
                } else if (attrs instanceof Map) {
                    battAttr = [currentValue: attrs["battery"]]
                }

                def rawBatt = battAttr?.currentValue
                if (battAttr == null) return   // no battery attribute — not a battery device
                def level = (rawBatt != null && rawBatt.toString().isNumber()) ? rawBatt.toDouble() : null

                if (level != null && level <= lowBatteryLevel) {
                    def critical = (level <= criticalBatteryLevel)
                    results << [
                        device     : null,
                        displayName: displayName,
                        hubLabel   : hubLabel,
                        linkUrl    : "http://${ip}/device/edit/${devId}",
                        level      : level,
                        critical   : critical
                    ]
                }
            }
        } catch (Exception e) {
            log.warn "${hubLabel} device ${devId}: error in fetchRemoteLowBatteryRows — ${e.message}"
        }
    }

    return results
}

// ── Fetch rows for generateActivityTable ─────────────────────────────────────
private List fetchRemoteActivityRows(int hubNum, Date rightNow, long thresholdMillis) {
    def results  = []
    def hubLabel = settings["hub${hubNum}Label"] ?: "Hub ${hubNum}"
    def ip       = settings["hub${hubNum}Ip"]
    def appId    = settings["hub${hubNum}AppId"]
    def token    = settings["hub${hubNum}Token"]

    def selectedIds = normalizeRemoteSelectionList(settings["hub${hubNum}SelectedDevices"])
    def excludeIds  = (settings["hub${hubNum}ExcludeIds"] ?: "").split(",").collect { it.trim() }.findAll { it } as Set

    if (!selectedIds || !ip || !appId || !token) return results

    def fs = '\u2007'

    selectedIds.each { devId ->
        if (excludeIds.contains(devId)) return
        try {
            def displayName  = null
            def batteryLevel = null
            def devType      = ""
            def hasBattery   = false
            Date lastActivity = null

            // Fetch device details
            httpGet([uri:"http://${ip}/apps/api/${appId}/devices/${devId}?access_token=${token}",
                     contentType:"application/json", timeout:10]) { resp ->
                if (resp.status != 200 || !resp.data) return
                def dev = resp.data

                displayName  = (dev.label ?: dev.name ?: "Unknown").toString()
                devType      = (dev.type ?: "").toString().toLowerCase()
                lastActivity = parseRemoteLastActivity(dev.lastActivity, hubLabel, devId)

                def attrs    = dev.attributes
                def battAttr = null
                if (attrs instanceof List) {
                    battAttr = attrs.find { a -> a?.name?.toString() == "battery" }
                } else if (attrs instanceof Map) {
                    battAttr = [currentValue: attrs["battery"]]
                }
                hasBattery = (battAttr != null)
                def rawBatt = battAttr?.currentValue
                batteryLevel = (rawBatt != null && rawBatt.toString().isNumber()) ? rawBatt.toInteger() : null
            }

            if (!displayName) return   // device fetch failed
            if (!hasBattery)  return   // no battery attribute — not a battery device
            def excludeVirt = settings["excludeVirtual"] ?: false
            if (excludeVirt && (devType.contains("virtual") || displayName.startsWith("VD "))) return

            // Fallback: derive lastActivity from most-recent event
            if (lastActivity == null) {
                try {
                    httpGet([uri:"http://${ip}/apps/api/${appId}/devices/${devId}/events?access_token=${token}",
                             contentType:"application/json", timeout:10]) { evResp ->
                        if (evResp.status == 200 && evResp.data instanceof List && evResp.data.size() > 0) {
                            lastActivity = parseRemoteLastActivity(evResp.data[0].date ?: evResp.data[0].time, hubLabel, "${devId}-evt")
                        }
                    }
                } catch (evEx) {
                    if (enableLogging) log.debug "${hubLabel} device ${devId}: events fetch failed — ${evEx.message}"
                }
            }

            def overdue = lastActivity ? (rightNow.time - lastActivity.time > thresholdMillis) : true
            if (!overdue) return

            def levelStr = batteryLevel != null ? batteryLevel.toString().padLeft(3, fs) : "N/A".padLeft(3, fs)
            String lastStrUI, lastStrNote
            if (lastActivity) {
                def formatted = lastActivity.format("yyyy-MM-dd hh:mm a", location.timeZone)
                lastStrUI   = "<span style='color:red;'>${formatted}</span>"
                lastStrNote = formatted
            } else {
                lastStrUI   = "<span style='color:red;'>[Never]</span>"
                lastStrNote = "0000-00-00 00:00 xx"
            }

            results << [
                device      : null,
                displayName : displayName,
                hubLabel    : hubLabel,
                linkUrl     : "http://${ip}/device/edit/${devId}",
                lastActivity: lastActivity,
                lastStrUI   : lastStrUI,
                lastStrNote : lastStrNote,
                level       : levelStr,
                overdue     : true
            ]
        } catch (Exception e) {
            log.warn "${hubLabel} device ${devId}: error in fetchRemoteActivityRows — ${e.message}"
        }
    }

    return results
}

// Parse a lastActivity value returned by the Maker API.  Handles:
//   • Long / Number  — epoch milliseconds
//   • Numeric string — epoch milliseconds as string
//   • Date string    — "yyyy-MM-dd HH:mm:ss±HHmm", ISO-8601 with T, positive or NEGATIVE offset
// Both + and – timezone offsets are stripped before parsing so US hubs (-05:00 etc.) work correctly.
private Date parseRemoteLastActivity(def laVal, String hubLabel, def devId) {
    if (laVal == null) return null
    try {
        if (laVal instanceof Number) return new Date(laVal.toLong())
        def laStr = laVal.toString().trim()
        if (!laStr || laStr == "null") return null
        if (laStr.isLong()) return new Date(laStr.toLong())
        // Replace T separator, then strip trailing ±HH:mm or ±HHmm (handles both signs)
        def raw = laStr.replace('T', ' ').replaceAll(/[+\-]\d{2}:?\d{2}$/, '').trim()
        if (raw) return Date.parse("yyyy-MM-dd HH:mm:ss", raw)
    } catch (pe) {
        if (enableLogging) log.warn "${hubLabel} device ${devId}: could not parse lastActivity '${laVal}' — ${pe.message}"
    }
    return null
}
