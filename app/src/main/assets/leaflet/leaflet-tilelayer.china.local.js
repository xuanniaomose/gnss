// 本地化的天地图图层插件（无需连接unpkg）
(function() {
    // 天地图服务地址模板（使用你的密钥）
    var tiandituTemplates = {
        'TianDiTu.Normal.Map': {
            url: 'https://t{s}.tianditu.gov.cn/DataServer?T=vec_w&x={x}&y={y}&l={z}&tk={key}',
            subdomains: ['0', '1', '2', '3', '4', '5', '6', '7']
        },
        'TianDiTu.Normal.Annotion': {
            url: 'https://t{s}.tianditu.gov.cn/DataServer?T=cva_w&x={x}&y={y}&l={z}&tk={key}',
            subdomains: ['0', '1', '2', '3', '4', '5', '6', '7']
        }
    };

    // 创建本地图层
    L.TileLayer.ChinaProvider = L.TileLayer.extend({
        initialize: function(type, options) {
            var provider = tiandituTemplates[type];
            options = L.setOptions(this, options);
            options.subdomains = provider.subdomains;
            options.key = options.key || '';
            
            var url = provider.url.replace('{key}', options.key);
            L.TileLayer.prototype.initialize.call(this, url, options);
        }
    });

    L.tileLayer.chinaProvider = function(type, options) {
        return new L.TileLayer.ChinaProvider(type, options);
    };
})();