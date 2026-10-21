import common from '@ec-site/shared/messages/ja.json'
import admin from '../../messages/ja.json'

/**
 * 管理画面の文言（フロントの書き方 第 7 節）。共有の文言（common）と、管理画面の文言を合わせる。<br />
 * 最上位のキー（機能の名前と common）は重ならないので、浅く合わせる。
 */
export const messages = { ...common, ...admin }
