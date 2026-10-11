const getBrowser = function () {
	const UserAgent = navigator.userAgent.toLowerCase()
	const browserInfo = {}
	const browserArray = {
		IE: window.ActiveXObject || 'ActiveXObject' in window,
		// IE
		Chrome:
			UserAgent.indexOf('chrome') > -1 && UserAgent.indexOf('safari') > -1,
		// Chrome浏览器
		Firefox: UserAgent.indexOf('firefox') > -1,
		// 火狐浏览器
		Edge: UserAgent.indexOf('edg') > -1
		// Edge浏览器
	}
	for (let i in browserArray) {
		if (browserArray[i]) {
			let versions = ''
			if (i == 'IE') {
				versions = UserAgent.match(/(msie\s|trident.*rv:)([\w.]+)/)[2]
				alert('请使用其他浏览器打开')
			} else if (i == 'Chrome') {
				versions = UserAgent.match(/chrome\/([\d.]+)/)[1]
				if (i === 'Chrome' && parseInt(versions) < 85) {
					alert('请升级浏览器版本')
				}
			} else if (i == 'Firefox') {
				versions = UserAgent.match(/firefox\/([\d.]+)/)[1]
				if (parseInt(versions) < 77) {
					alert('请将火狐浏览器升级到版本77以上')
				}
			} else if (i == 'Edge') {
				versions = UserAgent.match(/edg\/([\d.]+)/)[1]
				if (parseInt(versions) < 85) {
					alert('请将Edge浏览器升级到版本85以上')
				}
			}
			browserInfo.type = i
			browserInfo.versions = parseInt(versions)
		}
	}
	return browserInfo
}

getBrowser()
