# Enhanced Email Tool Plugin for Joget

## Overview
The Enhanced Email Tool Plugin for Joget allows users to store all emails sent out in an audit table and captures the error message if unsuccessful.

## Features
- Capture all emails sent out in the email_audit table.
- Track all email details (To, Cc, Bcc, Subject, Body, Error)
- Easy integration in Joget processes.

## Installation
1. Download the plugin JAR file.
2. Navigate to **System Settings > Manage Plugins** in Joget.
3. Upload the JAR file and activate the plugin.

## Configuration
1. Add the **Enhanced Email Tool** element to a Joget process tool.
2. Configure the email tool and select the checkbox "Debugging".
3. Save and publish the process.

## Usage
- Create a datalist with a SQL load binder "SELECT * FROM app_fd_emailaudit".
- Drag in the required columns (e.g. To, Cc, Bcc, Subject, Body, Error).
- Add a list element in the UI builder and map it to the datalist created.

## Requirements
- Joget DX (Compatible with Joget DX7 and later)
- Camera-enabled device for scanning

## Support
For any issues or enhancements, please contact joget support

