-- Focused join-flow fixture: Kahlua does not provide global next.
next = nil
require = function() end
Events = setmetatable({}, {__index=function(t,k)
 local callbacks={}; local e={callbacks=callbacks}
 e.Add=function(f) callbacks[#callbacks+1]=f end
 e.Remove=function(f) for i=#callbacks,1,-1 do if callbacks[i]==f then table.remove(callbacks,i) end end end
 rawset(t,k,e); return e
end})
ISPanelJoypad={derive=function() return {} end}
UIFont={Small=1}
getTextManager=function() return {getFontHeight=function() return 12 end} end
isClient=function() return true end
getPerformanceLocal=function() return {["memory-max"]=8192} end
local sent={}
sendClientCommand=function(m,c,a) sent[#sent+1]={m,c,a} end
dofile("media/lua/client/StormRamAllocation.lua")
dofile("media/lua/client/StormServerBootstrapVerification.lua")
assert(#Events.OnTick.callbacks==2)
local callbacks={table.unpack(Events.OnTick.callbacks)}
for _,f in ipairs(callbacks) do f() end
assert(#Events.OnTick.callbacks==0 and #sent==2)
local replies={}
sendServerCommand=function(p,m,c,a) replies[#replies+1]={p,m,c,a} end
dofile("media/lua/server/StormServerBootstrapResponder.lua")
local handle=Events.OnClientCommand.callbacks[1]
handle("unrelated","ping",{},{}); assert(#replies==0)
local player={};handle("stormBootstrap","ping",player,{})
assert(#replies==1 and replies[1][1]==player and replies[1][4].enabled==false)
Storm={};handle("stormBootstrap","ping",player,{})
assert(replies[2][4].enabled==true)
print("PASS: next unavailable; one-shot join callbacks; targeted bootstrap replies")
