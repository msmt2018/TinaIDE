-- Test Script Tools Plugin for TinaIDE
-- Demonstrates various editor and utility operations

-- Plugin activation
function activate(context)
    tina.log.info("Test Script Tools plugin activated")

    -- Register commands
    registerCommands()

    -- Subscribe to events
    subscribeEvents()
end

-- Plugin deactivation
function deactivate()
    tina.log.info("Test Script Tools plugin deactivated")
end

-- Register all commands
function registerCommands()
    -- Convert selection to uppercase
    tina.commands.register("test.uppercase", function()
        local selection = tina.editor.getSelection()
        if selection and selection.text and #selection.text > 0 then
            local upper = string.upper(selection.text)
            tina.editor.replaceSelection(upper)
            tina.ui.showMessage("Converted to uppercase")
        else
            tina.ui.showMessage("No text selected")
        end
    end)

    -- Convert selection to lowercase
    tina.commands.register("test.lowercase", function()
        local selection = tina.editor.getSelection()
        if selection and selection.text and #selection.text > 0 then
            local lower = string.lower(selection.text)
            tina.editor.replaceSelection(lower)
            tina.ui.showMessage("Converted to lowercase")
        else
            tina.ui.showMessage("No text selected")
        end
    end)

    -- Trim trailing whitespace from all lines
    tina.commands.register("test.trimLines", function()
        local text = tina.editor.getText()
        if text then
            local trimmed = text:gsub("[ \t]+\n", "\n")
            trimmed = trimmed:gsub("[ \t]+$", "")
            tina.editor.setText(trimmed)
            tina.ui.showMessage("Trimmed trailing whitespace")
        end
    end)

    -- Sort lines alphabetically
    tina.commands.register("test.sortLines", function()
        local selection = tina.editor.getSelection()
        local text = selection and selection.text or tina.editor.getText()

        if text and #text > 0 then
            local lines = {}
            for line in text:gmatch("[^\n]+") do
                table.insert(lines, line)
            end
            table.sort(lines)
            local sorted = table.concat(lines, "\n")

            if selection and selection.text then
                tina.editor.replaceSelection(sorted)
            else
                tina.editor.setText(sorted)
            end
            tina.ui.showMessage("Lines sorted")
        end
    end)

    -- Duplicate current line
    tina.commands.register("test.duplicateLine", function()
        local cursor = tina.editor.getCursor()
        if cursor then
            local line = tina.editor.getLine(cursor.line)
            if line then
                tina.editor.insertAt(cursor.line + 1, 0, "\n" .. line)
                tina.ui.showMessage("Line duplicated")
            end
        end
    end)

    -- Count words in selection or document
    tina.commands.register("test.countWords", function()
        local selection = tina.editor.getSelection()
        local text = selection and selection.text or tina.editor.getText()

        if text then
            local wordCount = 0
            for _ in text:gmatch("%S+") do
                wordCount = wordCount + 1
            end

            local charCount = #text
            local lineCount = 1
            for _ in text:gmatch("\n") do
                lineCount = lineCount + 1
            end

            local message = string.format(
                "Words: %d | Characters: %d | Lines: %d",
                wordCount, charCount, lineCount
            )
            tina.ui.showMessage(message)
        end
    end)
end

-- Subscribe to editor events
function subscribeEvents()
    -- Log when file is saved
    tina.events.on("editor.save", function(data)
        tina.log.info("File saved: " .. (data.path or "unknown"))
    end)

    -- Log when file is opened
    tina.events.on("editor.open", function(data)
        tina.log.info("File opened: " .. (data.path or "unknown"))
    end)
end

-- Utility function: Split string by delimiter
function split(str, delimiter)
    local result = {}
    local pattern = string.format("([^%s]+)", delimiter)
    for match in str:gmatch(pattern) do
        table.insert(result, match)
    end
    return result
end

-- Utility function: Join table with delimiter
function join(tbl, delimiter)
    return table.concat(tbl, delimiter)
end
