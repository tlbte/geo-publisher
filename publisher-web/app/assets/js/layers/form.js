require ([
	'dojo/dom',
	'dojo/on',
	'dojo/dom-style',
	'dojo/_base/window',
	'dojo/query',
	'dojo/dom-attr',
	'dojo/dom-construct',
	'tree-select/tree-select',
	'publisher/Pager',
	'put-selector/put',
	
	'dojo/NodeList-traverse',
	'dojo/domReady!'
], function (dom, on, domStyle, win, query, domattr, domConstruct, TreeSelect, Pager, put) {
	var treeSelect = new TreeSelect ('.gp-tree-select', '.gp-tree-values', { firstIsDefault: true }),
		pager = new Pager ('.gp-tree-values .js-pager');
	var keywordbutton = dom.byId('add-keyword');
	var keywordlist = dom.byId('keyword-list');
	
	var inputEnable = dom.byId('input-enable');
	var jsTiledForm = dom.byId('js-tiled-form');
	if (inputEnable.checked) {
		domStyle.set(jsTiledForm, "display", "block");
	} else {
		domStyle.set(jsTiledForm, "display", "none");
	}
	
	on(keywordbutton,'click', function(evt) {
		var keywordinput = dom.byId('input-keyword').value;
		
		if(keywordinput !== "") {
			var el1 = put("div.keyword-item-block[value=$]", keywordinput);
			var el2a = put(el1, "input[type=hidden][name=$][value=$]", "keywords[]", keywordinput);
			var el2b = put(el1, "div.keyword-item.label.label-primary", keywordinput);
			var el3 = put(el2b, "button.close[type=button][aria-hidden=true][value=$]", keywordinput);
			el3.innerHTML = "&times;";
			
			put(keywordlist, el1);
		}
		
		dom.byId('input-keyword').value = "";
	});
	
	on(win.doc, ".close:click", function(event) {
		
		var valueItem = domattr.get(this, 'value');
		
		var itemToDel = query(this).parents(".keyword-item-block")[0];
		domConstruct.destroy(itemToDel);
	});
	
	on(inputEnable, 'click', function(evt) {
		if (inputEnable.checked) {
			domStyle.set(jsTiledForm, "display", "block");
		} else {
			domStyle.set(jsTiledForm, "display", "none");
		}
	});
});

$(function () {
	$('[data-toggle="popover"]').popover();
});
