package com.sma.admin.workspace.internal.service;

import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.iam.*;
import com.sma.admin.workspace.ShelfErrorCode;
import com.sma.admin.workspace.internal.persistence.*;
import tools.jackson.databind.json.JsonMapper;

/** Every operation is scoped to the authenticated owner, including super administrators. */
@Service @Transactional(readOnly=true)
public class ShelfService {
 private final ShelfMapper shelves; private final BookmarkMapper bookmarks; private final NoteMapper notes;
 private final AccountQuery accounts; private final PermissionQuery permissions; private final Validator validator;
 private final JsonMapper json=JsonMapper.builder().build();
 public ShelfService(ShelfMapper shelves,BookmarkMapper bookmarks,NoteMapper notes,
   AccountQuery accounts,PermissionQuery permissions,Validator validator){
  this.shelves=shelves;this.bookmarks=bookmarks;this.notes=notes;this.accounts=accounts;this.permissions=permissions;this.validator=validator;
 }
 public record Target(Long id,String title,String path,String icon){}
 public record Bookmark(Long id,Long menuId,String alias,int position,Target target){}
 public record Item(@NotBlank @Size(max=36) String id,@NotBlank @Size(max=500) String text,boolean done){}
 public record NoteInput(@NotBlank @Size(max=50) String title,@NotBlank @Pattern(regexp="text|checklist") String kind,
   @NotNull @Size(max=10000) String content,@NotNull @Size(max=100) List<@NotNull @Valid Item> items,
   @NotBlank @Pattern(regexp="neutral|purple|green|amber") String color,boolean pinned,Long relatedMenuId){}
 public record CreateNote(@NotBlank @Pattern(regexp="[a-f0-9-]{36}") String requestKey,@NotNull @Valid NoteInput note){}
 public record UpdateNote(@NotNull @Min(1) Long version,@NotNull @Valid NoteInput note){}
 public record Transition(@NotNull @Min(1) Long version,@NotBlank @Pattern(regexp="active|archived|trash") String status){}
 public record Note(Long id,String requestKey,String title,String kind,String content,List<Item> items,String color,
   boolean pinned,String status,Instant trashedAt,Long relatedMenuId,Target relatedTarget,Long version,Instant updatedAt){}
 public record BookmarkInput(@NotNull @Min(0) Long menuId,@NotNull @Size(max=50) String alias){}
 public record BookmarkEdit(@NotNull @Min(0) Long version,@NotNull @Size(max=50) String alias){}
 public record Order(@NotNull @Min(0) Long version,@NotNull @Size(max=30) List<@NotNull Long> ids){}
 public record Mode(@NotNull @Min(0) Long version,@NotBlank @Pattern(regexp="drawer|floating|docked") String mode){}
 public record Board(String scope,String mode,Long version,List<Bookmark> bookmarks,List<Target> targets){}

 private <T> T valid(T value){if(value==null||!validator.validate(value).isEmpty())throw new BizException(CommonErrorCode.BAD_REQUEST);return value;}
 private LambdaQueryWrapper<ShelfEntity> ownerShelf(){
  var a=CurrentUser.require();return new LambdaQueryWrapper<ShelfEntity>().eq(ShelfEntity::getUserId,a.userId()).eq(ShelfEntity::getPortal,a.portal());
 }
 private LambdaQueryWrapper<BookmarkEntity> ownerBookmarks(){
  var a=CurrentUser.require();return new LambdaQueryWrapper<BookmarkEntity>().eq(BookmarkEntity::getUserId,a.userId()).eq(BookmarkEntity::getPortal,a.portal());
 }
 private LambdaQueryWrapper<NoteEntity> ownerNotes(){
  var a=CurrentUser.require();return new LambdaQueryWrapper<NoteEntity>().eq(NoteEntity::getUserId,a.userId()).eq(NoteEntity::getPortal,a.portal());
 }
 /** INSERT/UPDATE takes a database row lock until commit, also on first concurrent creation. */
 private ShelfEntity lock(){
  var a=CurrentUser.require();shelves.lock(IdWorker.getId(),a.tenantId(),a.userId(),a.portal());
  return shelves.selectOne(ownerShelf().last("FOR UPDATE"));
 }
 private void version(long actual,long expected){if(actual!=expected)throw new BizException(ShelfErrorCode.CONFLICT);}
 private void changed(ShelfEntity shelf){shelf.setVersion(shelf.getVersion()+1);shelves.updateById(shelf);}
 public List<Target> targets(){
  var a=CurrentUser.require();var result=new ArrayList<Target>();result.add(new Target(0L,"","/","lucide:house"));
  accounts.findById(a.userId()).filter(v->v.enabled()&&v.tenantId()==a.tenantId()&&v.portal().equals(a.portal())).ifPresent(account->
   flatten(permissions.menus(account.roleIds(),account.superAdmin()),result));
  return List.copyOf(result);
 }
 private void flatten(List<MenuView> menus,List<Target> out){
  for(var m:menus){
   String p=m.path();
   if("MENU".equals(m.type())&&"ROUTE".equals(m.openMode())&&p!=null&&p.startsWith("/")&&!p.startsWith("//")&&!p.contains("\\")&&!p.contains("?")&&!p.contains("#"))
    out.add(new Target(m.id(),m.name(),p,m.icon()));
   flatten(m.children(),out);
  }
 }
 private Map<Long,Target> targetMap(){var map=new LinkedHashMap<Long,Target>();targets().forEach(t->map.put(t.id(),t));return map;}
 private void requireTarget(Long id){if(id!=null&&!targetMap().containsKey(id))throw new BizException(ShelfErrorCode.UNAVAILABLE);}
 public Board board(){
  var shelf=shelves.selectOne(ownerShelf());var available=targetMap();
  var actor=CurrentUser.require();
  return new Board(actor.tenantId()+"."+actor.portal()+"."+actor.userId(),shelf==null?"drawer":shelf.getDisplayMode(),shelf==null?0L:shelf.getVersion(),
   bookmarks.selectList(ownerBookmarks().orderByAsc(BookmarkEntity::getPosition).orderByAsc(BookmarkEntity::getId)).stream()
    .map(b->new Bookmark(b.getId(),b.getMenuId(),b.getAlias(),b.getPosition(),available.get(b.getMenuId()))).toList(),List.copyOf(available.values()));
 }
 @Transactional public Board addBookmark(BookmarkInput input){
  valid(input);var shelf=lock();requireTarget(input.menuId());
  var existing=bookmarks.selectOne(ownerBookmarks().eq(BookmarkEntity::getMenuId,input.menuId()));
  if(existing!=null)return board();
  var all=bookmarks.selectList(ownerBookmarks());if(all.size()>=30)throw new BizException(ShelfErrorCode.LIMIT);
  var a=CurrentUser.require();var row=new BookmarkEntity();row.setUserId(a.userId());row.setPortal(a.portal());
  row.setMenuId(input.menuId());row.setAlias(input.alias().strip());row.setPosition(all.stream().mapToInt(BookmarkEntity::getPosition).max().orElse(-1)+1);
  bookmarks.insert(row);changed(shelf);return board();
 }
 private BookmarkEntity bookmark(long id){
  var row=bookmarks.selectOne(ownerBookmarks().eq(BookmarkEntity::getId,id));if(row==null)throw new BizException(CommonErrorCode.NOT_FOUND);return row;
 }
 @Transactional public Board editBookmark(long id,BookmarkEdit input){
  valid(input);var shelf=lock();var row=bookmark(id);version(shelf.getVersion(),input.version());
  row.setAlias(input.alias().strip());bookmarks.updateById(row);changed(shelf);return board();
 }
 @Transactional public Board removeBookmark(long id,long expected){
  var shelf=lock();var row=bookmark(id);version(shelf.getVersion(),expected);bookmarks.deleteById(row.getId());changed(shelf);return board();
 }
 @Transactional public Board reorder(Order input){
  valid(input);var shelf=lock();version(shelf.getVersion(),input.version());var all=bookmarks.selectList(ownerBookmarks());
  if(input.ids().size()!=all.size()||new HashSet<>(input.ids()).size()!=all.size()
   ||!new HashSet<>(input.ids()).equals(all.stream().map(BookmarkEntity::getId).collect(java.util.stream.Collectors.toSet())))
   throw new BizException(CommonErrorCode.BAD_REQUEST);
  for(var row:all){row.setPosition(input.ids().indexOf(row.getId()));bookmarks.updateById(row);}changed(shelf);return board();
 }
 @Transactional public Board mode(Mode input){
  valid(input);var shelf=lock();version(shelf.getVersion(),input.version());shelf.setDisplayMode(input.mode());changed(shelf);return board();
 }
 private NoteEntity note(long id){
  var row=notes.selectOne(ownerNotes().eq(NoteEntity::getId,id));
  if(row==null||expired(row))throw new BizException(CommonErrorCode.NOT_FOUND);return row;
 }
 private boolean expired(NoteEntity row){return "trash".equals(row.getStatus())&&row.getTrashedAt()!=null&&row.getTrashedAt().isBefore(Instant.now().minus(Duration.ofDays(30)));}
 private Note view(NoteEntity n,Map<Long,Target> available){
  return new Note(n.getId(),n.getRequestKey(),n.getTitle(),n.getKind(),n.getContent(),List.of(json.readValue(n.getItemsJson(),Item[].class)),
   n.getColor(),n.getPinned(),n.getStatus(),n.getTrashedAt(),n.getRelatedMenuId(),available.get(n.getRelatedMenuId()),n.getVersion(),n.getUpdatedAt());
 }
 @Transactional public List<Note> notes(){
  var a=CurrentUser.require();lock();notes.purgeExpired(a.userId(),a.portal(),Instant.now().minus(Duration.ofDays(30)));
  var available=targetMap();return notes.selectList(ownerNotes().orderByDesc(NoteEntity::getPinned).orderByDesc(NoteEntity::getUpdatedAt)).stream()
   .filter(n->!expired(n)).map(n->view(n,available)).toList();
 }
 public Note getNote(long id){return view(note(id),targetMap());}
 private void capacity(){if(notes.selectCount(ownerNotes().eq(NoteEntity::getStatus,"active"))>=200)throw new BizException(ShelfErrorCode.LIMIT);}
 private void apply(NoteEntity row,NoteInput in){
  valid(in);if(in.items().stream().map(Item::id).distinct().count()!=in.items().size())throw new BizException(CommonErrorCode.BAD_REQUEST);
  if(!Objects.equals(row.getRelatedMenuId(),in.relatedMenuId()))requireTarget(in.relatedMenuId());
  row.setTitle(in.title().strip());row.setKind(in.kind());row.setContent(in.content());row.setItemsJson(json.writeValueAsString(in.items()));
  row.setColor(in.color());row.setPinned(in.pinned());row.setRelatedMenuId(in.relatedMenuId());
 }
 @Transactional public Note create(CreateNote input){
  valid(input);lock();var previous=notes.selectOne(ownerNotes().eq(NoteEntity::getRequestKey,input.requestKey()));
  if(previous!=null){if(expired(previous))throw new BizException(CommonErrorCode.NOT_FOUND);return view(previous,targetMap());}
  capacity();var a=CurrentUser.require();var row=new NoteEntity();row.setUserId(a.userId());row.setPortal(a.portal());
  row.setRequestKey(input.requestKey());row.setStatus("active");row.setVersion(1L);apply(row,input.note());notes.insert(row);return view(row,targetMap());
 }
 @Transactional public Note update(long id,UpdateNote input){
  valid(input);lock();var row=note(id);version(row.getVersion(),input.version());
  if(!"active".equals(row.getStatus()))throw new BizException(CommonErrorCode.BAD_REQUEST);
  apply(row,input.note());row.setVersion(row.getVersion()+1);notes.updateById(row);return view(row,targetMap());
 }
 @Transactional public Note transition(long id,Transition input){
  valid(input);lock();var row=note(id);version(row.getVersion(),input.version());
  if(row.getStatus().equals(input.status()))return view(row,targetMap());
  if("active".equals(input.status()))capacity();
  if("trash".equals(row.getStatus())&&!"active".equals(input.status()))throw new BizException(CommonErrorCode.BAD_REQUEST);
  row.setStatus(input.status());row.setTrashedAt("trash".equals(input.status())?Instant.now():null);
  row.setVersion(row.getVersion()+1);notes.updateById(row);return view(row,targetMap());
 }
}
