/**
 * 票号工具函数
 * 票号格式：{区域代码(6位)}{业务前缀(1位)}{序号(3位)}，如 440300A001
 * 前台显示只保留 {前缀}{序号}，如 A001
 */

/**
 * 获取前台显示用的票号（去掉区域代码前缀）
 * @param {string} ticketNo 完整票号，如 "440300A001"
 * @returns {string} 显示用票号，如 "A001"
 */
export function getDisplayTicketNo(ticketNo) {
  if (!ticketNo || ticketNo.length < 7) return ticketNo || ''
  return ticketNo.slice(-4)
}
