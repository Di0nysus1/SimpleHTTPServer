# SimpleHTTPServer

![Screenshot of the Website](preview.png)

**SimpleHTTPServer** is a lightweight, easy-to-use file sharing HTTP server written in Java. The project started small but has grown to ~2000 lines — so “Simple” refers more to the UX than the codebase. It’s designed for fast file transfers over a LAN (e.g. LAN parties) and aims to let you share files with friends using the full bandwidth of your network.

## Quickstart

1. Download & unzip the release.
2. Run `start.bat`.
3. Open the shown `http://...` address in your browser and start uploading/downloading.

No complex setup — just unpack and run.

## Key features

* Serve one or multiple directories via a friendly web UI (configure directories in the config).
* Upload and download files through the browser.
* **Preview mode** (optional): play videos and audio or view images and text directly in the browser.
* Thumbnails for videos (optional) — requires **ffmpeg** installed. Learn more: [https://www.ffmpeg.org/](https://www.ffmpeg.org/)
* “Download all” button: stream the directory as a ZIP archive on-the-fly (no temporary zip files required).
* Range-request support for media files (seeking in video/audio).
* Safe path handling (prevents directory traversal, serves only configured folders).
* Runs on Java 8 (and newer Java versions).
* Simple to use — intended for quick LAN file sharing (fast transfers, minimal friction).

## Why use it

Ideal for sharing files inside a LAN (e.g. with friends at a LAN party). The UI makes it trivial to upload, download or preview media without installing extra tools on clients.

## Requirements

* Java 8+ runtime
* (Optional) `ffmpeg` for video thumbnail generation
