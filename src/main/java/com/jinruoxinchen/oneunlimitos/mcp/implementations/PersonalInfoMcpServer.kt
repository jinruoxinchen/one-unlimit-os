package com.jinruoxinchen.oneunlimitos.mcp.implementations

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import com.jinruoxinchen.oneunlimitos.accessibility.OneAccessibilityService
import com.jinruoxinchen.oneunlimitos.mcp.HttpMcpServer
import com.jinruoxinchen.oneunlimitos.mcp.McpResource
import com.jinruoxinchen.oneunlimitos.mcp.McpResourceContent
import com.jinruoxinchen.oneunlimitos.mcp.McpTool
import com.jinruoxinchen.oneunlimitos.mcp.McpToolResponse
import com.jinruoxinchen.oneunlimitos.mcp.McpParameterSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP server implementation for Personal Information Management.
 * This server provides tools for interacting with calendars, contacts, and reminders.
 */
class PersonalInfoMcpServer(
    private val accessibilityService: OneAccessibilityService?
) : HttpMcpServer(
    id = "personal_info_mcp",
    name = "Personal Information Manager",
    baseUrl = "https://localhost:8080/personal-info-mcp"
) {
    private val TAG = "PersonalInfoMcpServer"
    
    // Cache for personal information data
    private val calendarCache = ConcurrentHashMap<String, String>()
    private val contactsCache = ConcurrentHashMap<String, String>()
    private val remindersCache = ConcurrentHashMap<String, String>()
    
    // Reference to content resolver
    private val contentResolver: ContentResolver?
        get() = accessibilityService?.context?.contentResolver
    
    // Context reference
    private val context: Context?
        get() = accessibilityService?.context
    
    // Override HTTP communication with direct implementation
    // In a real deployment, this would use actual HTTP endpoints
    
    override suspend fun connect(): Boolean {
        return true // In this prototype, always return connected
    }
    
    override fun isConnected(): Boolean {
        return true // Always return connected for the prototype
    }
    
    override suspend fun listTools(): List<McpTool> {
        return listOf(
            // Calendar tools
            McpTool(
                name = "list_calendar_events",
                description = "Lists calendar events for a specified time range",
                inputSchema = mapOf(
                    "start_date" to McpParameterSchema(
                        type = "string",
                        description = "Start date in YYYY-MM-DD format (defaults to today)",
                        required = false
                    ),
                    "end_date" to McpParameterSchema(
                        type = "string",
                        description = "End date in YYYY-MM-DD format (defaults to 7 days from start)",
                        required = false
                    ),
                    "calendar_name" to McpParameterSchema(
                        type = "string",
                        description = "Name of calendar to filter by (defaults to all calendars)",
                        required = false
                    )
                )
            ),
            
            McpTool(
                name = "create_calendar_event",
                description = "Creates a new calendar event",
                inputSchema = mapOf(
                    "title" to McpParameterSchema(
                        type = "string",
                        description = "Title of the event"
                    ),
                    "start_time" to McpParameterSchema(
                        type = "string",
                        description = "Start time in YYYY-MM-DD HH:MM format"
                    ),
                    "end_time" to McpParameterSchema(
                        type = "string",
                        description = "End time in YYYY-MM-DD HH:MM format"
                    ),
                    "description" to McpParameterSchema(
                        type = "string",
                        description = "Description of the event",
                        required = false
                    ),
                    "location" to McpParameterSchema(
                        type = "string",
                        description = "Location of the event",
                        required = false
                    ),
                    "calendar_name" to McpParameterSchema(
                        type = "string",
                        description = "Name of calendar to add event to (defaults to primary)",
                        required = false
                    )
                )
            ),
            
            // Contacts tools
            McpTool(
                name = "list_contacts",
                description = "Lists contacts with optional filtering",
                inputSchema = mapOf(
                    "query" to McpParameterSchema(
                        type = "string",
                        description = "Search query to filter contacts",
                        required = false
                    ),
                    "limit" to McpParameterSchema(
                        type = "integer",
                        description = "Maximum number of contacts to return (default: 10)",
                        required = false
                    )
                )
            ),
            
            McpTool(
                name = "get_contact_details",
                description = "Gets detailed information for a specific contact",
                inputSchema = mapOf(
                    "contact_name" to McpParameterSchema(
                        type = "string",
                        description = "Name of the contact to retrieve details for"
                    )
                )
            ),
            
            // Reminders tools
            McpTool(
                name = "list_reminders",
                description = "Lists reminders with optional filtering",
                inputSchema = mapOf(
                    "completed" to McpParameterSchema(
                        type = "boolean",
                        description = "Whether to show completed reminders (default: false)",
                        required = false
                    ),
                    "limit" to McpParameterSchema(
                        type = "integer",
                        description = "Maximum number of reminders to return (default: 10)",
                        required = false
                    )
                )
            ),
            
            McpTool(
                name = "create_reminder",
                description = "Creates a new reminder",
                inputSchema = mapOf(
                    "title" to McpParameterSchema(
                        type = "string",
                        description = "Title of the reminder"
                    ),
                    "due_date" to McpParameterSchema(
                        type = "string",
                        description = "Due date in YYYY-MM-DD format",
                        required = false
                    ),
                    "due_time" to McpParameterSchema(
                        type = "string",
                        description = "Due time in HH:MM format",
                        required = false
                    ),
                    "priority" to McpParameterSchema(
                        type = "string",
                        description = "Priority of the reminder (high, medium, low)",
                        required = false
                    )
                )
            ),
            
            McpTool(
                name = "mark_reminder_complete",
                description = "Marks a reminder as complete",
                inputSchema = mapOf(
                    "reminder_id" to McpParameterSchema(
                        type = "string",
                        description = "ID of the reminder to mark as complete"
                    )
                )
            )
        )
    }
    
    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): McpToolResponse {
        return when (toolName) {
            // Calendar tools
            "list_calendar_events" -> listCalendarEvents(parameters)
            "create_calendar_event" -> createCalendarEvent(parameters)
            
            // Contacts tools
            "list_contacts" -> listContacts(parameters)
            "get_contact_details" -> getContactDetails(parameters)
            
            // Reminders tools
            "list_reminders" -> listReminders(parameters)
            "create_reminder" -> createReminder(parameters)
            "mark_reminder_complete" -> markReminderComplete(parameters)
            
            else -> McpToolResponse(
                isError = true,
                content = "Unknown tool: $toolName"
            )
        }
    }
    
    override suspend fun listResources(): List<McpResource> {
        return listOf(
            McpResource(
                uri = "personal://calendars",
                name = "Available Calendars",
                description = "List of calendars available on the device"
            ),
            McpResource(
                uri = "personal://contacts/all",
                name = "All Contacts",
                description = "List of all contacts"
            ),
            McpResource(
                uri = "personal://reminders/active",
                name = "Active Reminders",
                description = "List of active reminders"
            )
        )
    }
    
    override suspend fun readResource(uri: String): McpResourceContent {
        return when (uri) {
            "personal://calendars" -> McpResourceContent(getAvailableCalendars())
            "personal://contacts/all" -> McpResourceContent(getAllContacts())
            "personal://reminders/active" -> McpResourceContent(getActiveReminders())
            else -> McpResourceContent("Unknown resource: $uri")
        }
    }
    
    /**
     * Tool implementations
     */
    
    /**
     * Calendar tools
     */
    
    private suspend fun listCalendarEvents(parameters: Map<String, Any>): McpToolResponse {
        return withContext(Dispatchers.IO) {
            try {
                // Parse date parameters
                val startDateStr = parameters["start_date"] as? String ?: getCurrentDateString()
                val endDateStr = parameters["end_date"] as? String ?: getDatePlusDaysString(startDateStr, 7)
                val calendarName = parameters["calendar_name"] as? String
                
                Log.i(TAG, "Listing calendar events from $startDateStr to $endDateStr")
                
                // In a real implementation, this would query the Calendar content provider
                // For the prototype, return simulated data
                val events = """
                    Calendar Events from $startDateStr to $endDateStr${calendarName?.let { " for $it" } ?: ""}:
                    
                    Date: $startDateStr
                    - 09:00 - 10:00: Team Meeting (Conference Room A)
                    - 12:00 - 13:00: Lunch with Sarah (Cafe Bistro)
                    
                    Date: ${getDatePlusDaysString(startDateStr, 1)}
                    - 10:30 - 11:30: Doctor Appointment (City Medical Center)
                    - 15:00 - 16:30: Project Review (Online Zoom Meeting)
                    
                    Date: ${getDatePlusDaysString(startDateStr, 2)}
                    - 08:00 - 09:00: Morning Exercise (Gym)
                    - 14:00 - 16:00: Client Presentation (Main Office)
                """.trimIndent()
                
                McpToolResponse(content = events)
            } catch (e: Exception) {
                Log.e(TAG, "Error listing calendar events", e)
                McpToolResponse(
                    isError = true,
                    content = "Error listing calendar events: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun createCalendarEvent(parameters: Map<String, Any>): McpToolResponse {
        return withContext(Dispatchers.IO) {
            try {
                val title = parameters["title"] as? String
                    ?: return@withContext McpToolResponse(
                        isError = true,
                        content = "Missing required parameter: title"
                    )
                
                val startTime = parameters["start_time"] as? String
                    ?: return@withContext McpToolResponse(
                        isError = true,
                        content = "Missing required parameter: start_time"
                    )
                
                val endTime = parameters["end_time"] as? String
                    ?: return@withContext McpToolResponse(
                        isError = true,
                        content = "Missing required parameter: end_time"
                    )
                
                val description = parameters["description"] as? String ?: ""
                val location = parameters["location"] as? String ?: ""
                val calendarName = parameters["calendar_name"] as? String ?: "Primary Calendar"
                
                Log.i(TAG, "Creating calendar event: $title from $startTime to $endTime")
                
                // In a real implementation, this would use the Calendar content provider
                // to create an actual calendar event
                
                // Store in cache for demonstration purposes
                val eventId = "event_${System.currentTimeMillis()}"
                calendarCache[eventId] = """
                    Title: $title
                    Start: $startTime
                    End: $endTime
                    Description: $description
                    Location: $location
                    Calendar: $calendarName
                """.trimIndent()
                
                McpToolResponse(
                    content = "Created calendar event: $title from $startTime to $endTime in $calendarName"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating calendar event", e)
                McpToolResponse(
                    isError = true,
                    content = "Error creating calendar event: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Contacts tools
     */
    
    private suspend fun listContacts(parameters: Map<String, Any>): McpToolResponse {
        return withContext(Dispatchers.IO) {
            try {
                val query = parameters["query"] as? String
                val limit = (parameters["limit"] as? Number)?.toInt() ?: 10
                
                Log.i(TAG, "Listing contacts, query: ${query ?: "none"}, limit: $limit")
                
                // In a real implementation, this would query the Contacts content provider
                // For the prototype, return simulated data
                val contacts = """
                    Contacts${query?.let { " matching '$it'" } ?: ""}:
                    
                    1. John Smith
                       Phone: +1 (555) 123-4567
                       Email: john.smith@example.com
                    
                    2. Sarah Johnson
                       Phone: +1 (555) 234-5678
                       Email: sarah.j@example.com
                    
                    3. Michael Chen
                       Phone: +1 (555) 345-6789
                       Email: michael.chen@example.com
                    
                    4. Emily Davis
                       Phone: +1 (555) 456-7890
                       Email: emily.davis@example.com
                    
                    5. David Wilson
                       Phone: +1 (555) 567-8901
                       Email: david.wilson@example.com
                """.trimIndent()
                
                McpToolResponse(content = contacts)
            } catch (e: Exception) {
                Log.e(TAG, "Error listing contacts", e)
                McpToolResponse(
                    isError = true,
                    content = "Error listing contacts: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun getContactDetails(parameters: Map<String, Any>): McpToolResponse {
        return withContext(Dispatchers.IO) {
            try {
                val contactName = parameters["contact_name"] as? String
                    ?: return@withContext McpToolResponse(
                        isError = true,
                        content = "Missing required parameter: contact_name"
                    )
                
                Log.i(TAG, "Getting contact details for: $contactName")
                
                // In a real implementation, this would query the Contacts content provider
                // For the prototype, return simulated data
                val contactDetails = when (contactName.lowercase()) {
                    "john smith" -> """
                        Contact: John Smith
                        
                        Phone Numbers:
                        - Mobile: +1 (555) 123-4567
                        - Work: +1 (555) 123-4568
                        
                        Email Addresses:
                        - Personal: john.smith@example.com
                        - Work: john.smith@work.com
                        
                        Address:
                        123 Main Street
                        Anytown, CA 12345
                        
                        Company: Acme Corporation
                        Title: Senior Manager
                        
                        Birthday: January 15, 1980
                    """.trimIndent()
                    
                    "sarah johnson" -> """
                        Contact: Sarah Johnson
                        
                        Phone Numbers:
                        - Mobile: +1 (555) 234-5678
                        - Home: +1 (555) 234-5679
                        
                        Email Addresses:
                        - Personal: sarah.j@example.com
                        - Work: sarah.johnson@work.com
                        
                        Address:
                        456 Oak Avenue
                        Othertown, NY 67890
                        
                        Company: Global Enterprises
                        Title: Marketing Director
                        
                        Birthday: March 22, 1985
                    """.trimIndent()
                    
                    else -> "No detailed information found for $contactName"
                }
                
                McpToolResponse(content = contactDetails)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting contact details", e)
                McpToolResponse(
                    isError = true,
                    content = "Error getting contact details: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Reminders tools
     */
    
    private suspend fun listReminders(parameters: Map<String, Any>): McpToolResponse {
        return withContext(Dispatchers.IO) {
            try {
                val showCompleted = parameters["completed"] as? Boolean ?: false
                val limit = (parameters["limit"] as? Number)?.toInt() ?: 10
                
                Log.i(TAG, "Listing reminders, completed: $showCompleted, limit: $limit")
                
                // In a real implementation, this would query the Reminders content provider
                // For the prototype, return simulated data
                val reminders = """
                    ${if (showCompleted) "All" else "Active"} Reminders:
                    
                    1. [Active] Buy groceries
                       Due: ${getCurrentDateString()} 18:00
                       Priority: High
                       
                    2. [Active] Call mom
                       Due: ${getCurrentDateString()} 20:00
                       Priority: Medium
                       
                    3. [Active] Finish project proposal
                       Due: ${getDatePlusDaysString(getCurrentDateString(), 1)} 17:00
                       Priority: High
                       
                    4. [Active] Pay utility bills
                       Due: ${getDatePlusDaysString(getCurrentDateString(), 2)} 12:00
                       Priority: Medium
                       
                    ${if (showCompleted) """
                    5. [Completed] Send email to team
                       Completed: ${getDateMinusDaysString(getCurrentDateString(), 1)} 15:30
                       
                    6. [Completed] Schedule dentist appointment
                       Completed: ${getDateMinusDaysString(getCurrentDateString(), 2)} 10:00
                    """ else ""}
                """.trimIndent()
                
                McpToolResponse(content = reminders)
            } catch (e: Exception) {
                Log.e(TAG, "Error listing reminders", e)
                McpToolResponse(
                    isError = true,
                    content = "Error listing reminders: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun createReminder(parameters: Map<String, Any>): McpToolResponse {
        return withContext(Dispatchers.IO) {
            try {
                val title = parameters["title"] as? String
                    ?: return@withContext McpToolResponse(
                        isError = true,
                        content = "Missing required parameter: title"
                    )
                
                val dueDate = parameters["due_date"] as? String ?: getCurrentDateString()
                val dueTime = parameters["due_time"] as? String ?: "18:00"
                val priority = parameters["priority"] as? String ?: "medium"
                
                Log.i(TAG, "Creating reminder: $title due on $dueDate at $dueTime")
                
                // In a real implementation, this would use the Reminders content provider
                // to create an actual reminder
                
                // Store in cache for demonstration purposes
                val reminderId = "reminder_${System.currentTimeMillis()}"
                remindersCache[reminderId] = """
                    Title: $title
                    Due: $dueDate $dueTime
                    Priority: $priority
                    Status: Active
                """.trimIndent()
                
                McpToolResponse(
                    content = "Created reminder: '$title' due on $dueDate at $dueTime with $priority priority (ID: $reminderId)"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error creating reminder", e)
                McpToolResponse(
                    isError = true,
                    content = "Error creating reminder: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun markReminderComplete(parameters: Map<String, Any>): McpToolResponse {
        return withContext(Dispatchers.IO) {
            try {
                val reminderId = parameters["reminder_id"] as? String
                    ?: return@withContext McpToolResponse(
                        isError = true,
                        content = "Missing required parameter: reminder_id"
                    )
                
                Log.i(TAG, "Marking reminder complete: $reminderId")
                
                // In a real implementation, this would use the Reminders content provider
                // to update an actual reminder
                
                // Update cache for demonstration purposes
                if (remindersCache.containsKey(reminderId)) {
                    val reminderInfo = remindersCache[reminderId]?.replace("Status: Active", "Status: Completed")
                    reminderInfo?.let { remindersCache[reminderId] = it }
                    
                    McpToolResponse(
                        content = "Marked reminder $reminderId as complete"
                    )
                } else {
                    McpToolResponse(
                        isError = true,
                        content = "Reminder not found: $reminderId"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking reminder complete", e)
                McpToolResponse(
                    isError = true,
                    content = "Error marking reminder complete: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Resource implementations
     */
    
    private suspend fun getAvailableCalendars(): String {
        // In a real implementation, this would query the Calendar content provider
        // For the prototype, return simulated data
        return """
            Available Calendars:
            
            1. Primary Calendar
               Owner: User
               Color: Blue
               
            2. Work Calendar
               Owner: User (Work)
               Color: Red
               
            3. Family Calendar
               Owner: Family Group
               Color: Green
               
            4. Birthdays
               Owner: Contacts
               Color: Purple
        """.trimIndent()
    }
    
    private suspend fun getAllContacts(): String {
        // In a real implementation, this would query the Contacts content provider
        // For the prototype, return simulated data
        return """
            All Contacts:
            
            John Smith
            Sarah Johnson
            Michael Chen
            Emily Davis
            David Wilson
            Jennifer Lee
            Robert Brown
            Lisa Martinez
            James Taylor
            Elizabeth Anderson
        """.trimIndent()
    }
    
    private suspend fun getActiveReminders(): String {
        // In a real implementation, this would query the Reminders content provider
        // For the prototype, return simulated data
        return """
            Active Reminders:
            
            1. Buy groceries
               Due: ${getCurrentDateString()} 18:00
               Priority: High
               
            2. Call mom
               Due: ${getCurrentDateString()} 20:00
               Priority: Medium
               
            3. Finish project proposal
               Due: ${getDatePlusDaysString(getCurrentDateString(), 1)} 17:00
               Priority: High
               
            4. Pay utility bills
               Due: ${getDatePlusDaysString(getCurrentDateString(), 2)} 12:00
               Priority: Medium
        """.trimIndent()
    }
    
    /**
     * Helper methods for date handling
     */
    
    private fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
    
    private fun getDatePlusDaysString(dateStr: String, days: Int): String {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.parse(dateStr) ?: Date()
            
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.DAY_OF_YEAR, days)
            
            return dateFormat.format(calendar.time)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating date plus days", e)
            return dateStr
        }
    }
    
    private fun getDateMinusDaysString(dateStr: String, days: Int): String {
        return getDatePlusDaysString(dateStr, -days)
    }
}
