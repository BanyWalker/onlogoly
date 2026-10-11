const getBrowser = function () {
	const userAgent = navigator.userAgent.toLowerCase();
	const browserInfo: { type?: string; versions?: number } = {};
	const browserArray: Record<string, boolean> = {
		IE: "ActiveXObject" in window,
		Chrome: userAgent.indexOf("chrome") > -1 && userAgent.indexOf("safari") > -1,
		Firefox: userAgent.indexOf("firefox") > -1,
		Edge: userAgent.indexOf("edg") > -1
	};

	for (const name in browserArray) {
		if (browserArray[name]) {
			let versions = "";

			if (name === "IE") {
				versions = userAgent.match(/(msie\s|trident.*rv:)([\w.]+)/)?.[2] ?? "0";
				alert("请使用其他浏览器打开");
			} else if (name === "Chrome") {
				versions = userAgent.match(/chrome\/([\d.]+)/)?.[1] ?? "0";
				if (parseInt(versions) < 85) {
					alert("请升级浏览器版本");
				}
			} else if (name === "Firefox") {
				versions = userAgent.match(/firefox\/([\d.]+)/)?.[1] ?? "0";
				if (parseInt(versions) < 77) {
					alert("请将火狐浏览器升级到版本77以上");
				}
			} else if (name === "Edge") {
				versions = userAgent.match(/edg\/([\d.]+)/)?.[1] ?? "0";
				if (parseInt(versions) < 85) {
					alert("请将Edge浏览器升级到版本85以上");
				}
			}

			browserInfo.type = name;
			browserInfo.versions = parseInt(versions);
		}
	}

	return browserInfo;
};

getBrowser();
