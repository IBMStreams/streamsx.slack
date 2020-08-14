---
title: "Toolkit Usage Overview"
permalink: /docs/user/overview/
excerpt: "How to use this toolkit."
last_modified_at: 2020-08-14T12:37:48-04:00
redirect_from:
   - /theme-setup/
sidebar:
   nav: "userdocs"
---
{% include toc %}
{%include editme %}


## Setup Instructions

### Prerequisites

1.  **streamsx.slack** -- Download the toolkit by cloning it from the official [repository](https://github.com/IBMStreams/streamsx.slack) or download a [release](https://github.com/IBMStreams/streamsx.slack/releases).
1.  **An incoming webhook URL** for your Slack channel — You can generate one [here](https://slack.com/apps/A0F7XDUAZ-incoming-webhooks) (you may need to ask your team admin for permission to add a webhook).


Clone and build the streamsx.slack toolkit by running the following commands:

```
git clone https://github.com/IBMStreams/streamsx.slack.git
cd streamsx.slack/com.ibm.streamsx.slack
ant all
```

Add the toolkit as a dependency to your Streams application.


Note: If you don’t have a Streams application to test with, there are samples in the toolkit’s `samples` folder you may reference.


## Configuration

 Parameter               | Type            | Description
---------------------   |--------------- |---------------- 
 **slackConfiguration**  | _rstring_       | Specifies name of the Streams application configuration containing the Slack incoming WebHook URL to send messages to. 
 **slackUrl**            | _rstring_       | Specifies the Slack incoming WebHook URL to send messages to. 

## Sample Message

![Import](/streamsx.slack/doc/images/slack-message.png)

## SPLDOC

[SPLDoc](https://ibmstreams.github.io/streamsx.slack/doc/spldoc/html/index.html)


