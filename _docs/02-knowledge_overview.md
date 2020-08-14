---
title: "Get Started"
permalink: /docs/knowledge/overview/
excerpt: "Basic knowledge of the toolkits technical domain and usage."
last_modified_at: 2020-08-14T15:15:48-04:00
redirect_from:
   - /theme-setup/
sidebar:
   nav: "knowledgedocs"
---
{% include toc %}
{% include editme %}

**Slack** is a cloud-based set of team collaboration tools used for real-time messaging, archiving and search. It is commonly used in corporate settings to bring communication between teams into one place.   With the new Slack toolkit, you can now use Slack as a hub for messages and alerts from Streams applications.

This can be useful for monitoring, for example, you could use it to inform admins or developers of metrics from a running Streams application, or update the results of analysis.

This article will demonstrate how to send messages to any Slack channel from Streams using the **streamsx.slack** toolkit and its `SendSlackMessage` operator.


## Prerequisites

To start sending Slack messages with streams applications, you'll need:

* IBM Streams
* The streamsx.slack toolkit - Download a [release](https://github.com/IBMStreams/streamsx.slack/releases) or clone it from this repository.
* An incoming WebHook URL for your Slack channel - You can generate one [here](https://slack.com/apps/A0F7XDUAZ-incoming-webhooks) (you may need to ask your team admin for permission to add a web-hook).

## Setup Instructions

* Download and unpack the latest release from the toolkit's releases page.
* Add the toolkit as a dependency to your Streams application.

Note: If you don't have a Streams application to test with, there are samples in the toolkit's samples folder you may reference.

## Using the Toolkit

Now that the toolkit has been added as a dependency to your application. Simply connect the SendSlackMessage operator to an operator outputting messages.

The slackUrl is the only required parameter. Give this parameter the incoming WebHook URL you generated from your Slack channel in the Prerequisites section.

## Defining the Message?s Content

By default, the operator looks for an attribute called "message" on the incoming tuple. However, you can select which tuple attribute contains the message you want to send to your slack channel using the `messageAttribute` parameter. This parameter accepts an incoming attribute of type `rstring`.

## Example

There is an example Streams application that outputs "Hello World!" messages in the toolkit's sample folder. Here is SPL code for the 2 operators in the main composite:

```

/**
 * GenerateMessage outputs a 'Hello World' message every 5 minutes.
 */
stream <rstring helloMessage>GenerateMessage = Beacon()
{
  param
    period : 300.0 ;
  output
    GenerateMessage: helloMessage = "Hello World!";
}

/**
 * SendMessage sends the generated message to the given slack URL.
 */
() as SendMessage = SendSlackMessage(GenerateMessage)
{
  param
    slackUrl : getSubmissionTimeValue("slackUrl") ;
    messageAttribute : helloMessage ;
}
```

The `SendSlackMessage` operator has 2 parameters defined here:

* slackUrl - The incoming Slack WebHook URL to send messages to. This is defined during submission time.
* messageAttribute - As was mentioned before, the `SendSlackMessage` operator reads from an incoming message attribute. In this case, the `helloMessage` attribute contains our message so we need to tell the operator to read from this attribute, instead.

If all goes well, your Slack channel should get a message like this:

![Import](/streamsx.slack/doc/images/hello-world-slack-message.png)


## Customization

When you configure your Incoming Webhook, you?ll notice that you can set custom values for usernames and icons.  The `SendSlackMessage` operator allows you to override these values when necessary.

### Defining Custom Usernames

Custom message usernames can be defined by using the optional usernameAttribute parameter. The `usernameAttribute` parameter overrides your configuration's username, if defined. This parameters also accepts an incoming attribute of type rstring.

### Defining Custom Icons

Custom message icons can be defined by using the optional `iconUrlAttribute` and `iconEmojiAttribute` parameters. In your Incoming WebHook's configuration, you'll notice that you can set a custom icon for your messages (you can only choose one or the other). The `iconUrlAttribute` and `iconEmojiAttribute` parameters override your configuration's icon, if defined.
The `iconUrlAttribute` just contains a link to your desired image. The `iconEmojiAttribute` contains an icon emoji code (eg. ":smile:", ":heart:", ":no_entry_sign:", etc).  All the possible emoji codes can be found here.
The `usernameAttribute`, `iconUrlAttribute`, and `iconEmojiAttribute` parameters work in exactly the same way as the `messageAttribute`. 

Please look at the other samples in the toolkit's samples folder for a better understanding.





