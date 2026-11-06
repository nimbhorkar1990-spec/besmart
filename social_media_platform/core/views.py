from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from django.contrib.auth.decorators import login_required, user_passes_test
from django.contrib import messages
from django.http import JsonResponse, HttpResponseForbidden
from django.db.models import Q, Count
from django.views.decorators.http import require_POST
import json

from .models import (
    UserProfile, Connection, Follow, Block,
    Group, CompanyPage, Post, Comment,
    Message, Notification, ReportedContent
)
from .forms import (
    UserRegistrationForm, UserProfileForm, PostForm,
    CommentForm, GroupForm, CompanyPageForm, MessageForm, ReportForm
)


# Helper: send notification
def create_notification(recipient, sender, notification_type, text, link='', post=None):
    if recipient == sender:
        return None
    return Notification.objects.create(
        recipient=recipient,
        sender=sender,
        notification_type=notification_type,
        text=text,
        link=link,
        post=post
    )


# -------------------------------------------------------------
# 1. Authentication Views
# -------------------------------------------------------------
def register_view(request):
    if request.user.is_authenticated:
        return redirect('feed')
    if request.method == 'POST':
        form = UserRegistrationForm(request.POST)
        if form.is_valid():
            user = form.save(commit=False)
            user.set_password(form.cleaned_data['password'])
            user.first_name = form.cleaned_data['first_name']
            user.last_name = form.cleaned_data['last_name']
            user.save()

            # Profile already created by post_save signal
            profile = user.profile
            profile.mobile_number = form.cleaned_data['mobile_number']
            profile.save()

            messages.success(request, "Registration successful! Welcome to the SmartSearch Social Media Platform.")
            login(request, user)
            return redirect('feed')
    else:
        form = UserRegistrationForm()
    return render(request, 'auth/register.html', {'form': form})


def login_view(request):
    if request.user.is_authenticated:
        return redirect('feed')
    if request.method == 'POST':
        username_or_email = request.POST.get('username', '').strip()
        password = request.POST.get('password', '')

        # Support login by username or email
        user_obj = None
        if '@' in username_or_email:
            try:
                user_match = User.objects.get(email__iexact=username_or_email)
                user_obj = authenticate(request, username=user_match.username, password=password)
            except User.DoesNotExist:
                user_obj = None
        else:
            user_obj = authenticate(request, username=username_or_email, password=password)

        if user_obj is not None:
            if not user_obj.is_active:
                messages.error(request, "Your account has been deactivated. Please contact support.")
            else:
                login(request, user_obj)
                messages.success(request, f"Welcome back, {user_obj.get_full_name() or user_obj.username}!")
                next_url = request.GET.get('next', 'feed')
                return redirect(next_url)
        else:
            messages.error(request, "Invalid username/email or password.")
    return render(request, 'auth/login.html')


def logout_view(request):
    logout(request)
    messages.info(request, "You have been logged out successfully.")
    return redirect('login')


def forgot_password_view(request):
    if request.method == 'POST':
        email = request.POST.get('email', '').strip()
        user_exists = User.objects.filter(email__iexact=email).exists()
        if user_exists:
            messages.success(request, f"Password reset instructions and verification code have been simulated for {email}.")
            return redirect('reset_password')
        else:
            messages.error(request, "No registered account found with that email address.")
    return render(request, 'auth/forgot_password.html')


def reset_password_view(request):
    if request.method == 'POST':
        username = request.POST.get('username', '').strip()
        new_password = request.POST.get('new_password', '')
        confirm_password = request.POST.get('confirm_password', '')
        if new_password != confirm_password:
            messages.error(request, "Passwords do not match.")
        elif len(new_password) < 6:
            messages.error(request, "Password must be at least 6 characters.")
        else:
            try:
                u = User.objects.get(username__iexact=username)
                u.set_password(new_password)
                u.save()
                messages.success(request, "Password successfully reset! You can now log in with your new password.")
                return redirect('login')
            except User.DoesNotExist:
                messages.error(request, "User not found.")
    return render(request, 'auth/reset_password.html')


# -------------------------------------------------------------
# 2. Home Feed & Post Management
# -------------------------------------------------------------
@login_required
def feed_view(request):
    tab = request.GET.get('tab', 'all')  # 'all', 'following', 'saved'
    user = request.user

    # Exclude blocked users
    blocked_ids = Block.objects.filter(Q(blocker=user) | Q(blocked=user)).values_list('blocker_id', 'blocked_id')
    excluded_user_ids = set()
    for b1, b2 in blocked_ids:
        excluded_user_ids.add(b1 if b1 != user.id else b2)

    posts_query = Post.objects.exclude(author_id__in=excluded_user_ids).select_related('author', 'author__profile', 'group', 'company').prefetch_related('likes', 'comments', 'saves')

    if tab == 'following':
        # Posts from connected users or followed users
        following_ids = list(Follow.objects.filter(follower=user).values_list('following_id', flat=True))
        connected_ids = set()
        for conn in Connection.objects.filter((Q(sender=user) | Q(receiver=user)), status='accepted'):
            connected_ids.add(conn.receiver_id if conn.sender_id == user.id else conn.sender_id)
        target_ids = set(following_ids).union(connected_ids)
        target_ids.add(user.id)
        posts_query = posts_query.filter(author_id__in=target_ids)
    elif tab == 'saved':
        posts_query = posts_query.filter(saves=user)
    else:
        # 'all' feed - public posts or own posts or connections posts
        posts_query = posts_query.filter(
            Q(visibility='public') | Q(author=user)
        )

    posts = posts_query[:50]
    post_form = PostForm()
    comment_form = CommentForm()

    # Recommended people to connect
    suggested_users = User.objects.exclude(id=user.id).exclude(id__in=excluded_user_ids).select_related('profile')[:5]

    # Trending groups
    popular_groups = Group.objects.annotate(num_members=Count('members')).order_by('-num_members')[:4]

    # Trending companies
    featured_companies = CompanyPage.objects.annotate(num_followers=Count('followers')).order_by('-num_followers')[:4]

    context = {
        'posts': posts,
        'post_form': post_form,
        'comment_form': comment_form,
        'active_tab': tab,
        'suggested_users': suggested_users,
        'popular_groups': popular_groups,
        'featured_companies': featured_companies,
    }
    return render(request, 'feed/home_feed.html', context)


@login_required
@require_POST
def create_post_view(request):
    form = PostForm(request.POST, request.FILES)
    group_id = request.POST.get('group_id')
    company_id = request.POST.get('company_id')

    if form.is_valid():
        post = form.save(commit=False)
        post.author = request.user
        if group_id:
            try:
                post.group = Group.objects.get(id=group_id)
            except Group.DoesNotExist:
                pass
        if company_id:
            try:
                company = CompanyPage.objects.get(id=company_id)
                if company.owner == request.user:
                    post.company = company
            except CompanyPage.DoesNotExist:
                pass
        post.save()
        messages.success(request, "Your post has been published to the community!")
    else:
        messages.error(request, "Could not publish post. Please check the content.")

    if group_id:
        return redirect('group_detail', slug=post.group.slug)
    if company_id:
        return redirect('company_detail', slug=post.company.slug)
    return redirect('feed')


@login_required
def post_detail_view(request, post_id):
    post = get_object_or_404(Post.objects.select_related('author', 'author__profile'), id=post_id)
    comment_form = CommentForm()
    comments = post.comments.filter(parent__isnull=True).select_related('author', 'author__profile').prefetch_related('replies__author__profile')
    return render(request, 'feed/post_detail.html', {
        'post': post,
        'comments': comments,
        'comment_form': comment_form,
    })


@login_required
def edit_post_view(request, post_id):
    post = get_object_or_404(Post, id=post_id, author=request.user)
    if request.method == 'POST':
        form = PostForm(request.POST, request.FILES, instance=post)
        if form.is_valid():
            form.save()
            messages.success(request, "Post updated successfully!")
            return redirect('post_detail', post_id=post.id)
    else:
        form = PostForm(instance=post)
    return render(request, 'feed/edit_post.html', {'form': form, 'post': post})


@login_required
@require_POST
def delete_post_view(request, post_id):
    post = get_object_or_404(Post, id=post_id)
    if post.author == request.user or request.user.is_staff:
        post.delete()
        messages.success(request, "Post deleted successfully.")
    else:
        messages.error(request, "You are not authorized to delete this post.")
    return redirect('feed')


@login_required
@require_POST
def toggle_like_view(request, post_id):
    post = get_object_or_404(Post, id=post_id)
    user = request.user
    liked = False
    if post.likes.filter(id=user.id).exists():
        post.likes.remove(user)
    else:
        post.likes.add(user)
        liked = True
        create_notification(
            recipient=post.author,
            sender=user,
            notification_type='like',
            text=f"{user.get_full_name() or user.username} liked your post.",
            link=f"/post/{post.id}/",
            post=post
        )
    return JsonResponse({'liked': liked, 'likes_count': post.likes_count()})


@login_required
@require_POST
def toggle_save_view(request, post_id):
    post = get_object_or_404(Post, id=post_id)
    user = request.user
    saved = False
    if post.saves.filter(id=user.id).exists():
        post.saves.remove(user)
    else:
        post.saves.add(user)
        saved = True
    return JsonResponse({'saved': saved})


@login_required
@require_POST
def add_comment_view(request, post_id):
    post = get_object_or_404(Post, id=post_id)
    content = request.POST.get('content', '').strip()
    parent_id = request.POST.get('parent_id')

    if content:
        parent_comment = None
        if parent_id:
            try:
                parent_comment = Comment.objects.get(id=parent_id, post=post)
            except Comment.DoesNotExist:
                pass

        comment = Comment.objects.create(
            post=post,
            author=request.user,
            content=content,
            parent=parent_comment
        )

        # Send notification to post author or parent comment author
        if parent_comment and parent_comment.author != request.user:
            create_notification(
                recipient=parent_comment.author,
                sender=request.user,
                notification_type='reply',
                text=f"{request.user.get_full_name() or request.user.username} replied to your comment.",
                link=f"/post/{post.id}/",
                post=post
            )
        elif post.author != request.user:
            create_notification(
                recipient=post.author,
                sender=request.user,
                notification_type='comment',
                text=f"{request.user.get_full_name() or request.user.username} commented on your post.",
                link=f"/post/{post.id}/",
                post=post
            )

        messages.success(request, "Comment added.")
    return redirect('post_detail', post_id=post.id)


@login_required
@require_POST
def delete_comment_view(request, comment_id):
    comment = get_object_or_404(Comment, id=comment_id)
    post_id = comment.post.id
    if comment.author == request.user or comment.post.author == request.user or request.user.is_staff:
        comment.delete()
        messages.success(request, "Comment deleted.")
    return redirect('post_detail', post_id=post_id)


@login_required
@require_POST
def share_post_view(request, post_id):
    post = get_object_or_404(Post, id=post_id)
    post.shares_count += 1
    post.save()
    messages.success(request, "Post shared successfully to your network!")
    return redirect('feed')


# -------------------------------------------------------------
# 3. User Profile Management
# -------------------------------------------------------------
def profile_view(request, username):
    profile_user = get_object_or_404(User.objects.select_related('profile'), username__iexact=username)
    profile = profile_user.profile

    # Check privacy
    is_me = (request.user.is_authenticated and request.user == profile_user)
    is_connected = False
    has_pending = False
    has_sent = False
    is_following = False

    if request.user.is_authenticated and not is_me:
        is_connected = request.user.profile.is_connected_with(profile_user)
        has_pending = request.user.profile.has_pending_request_from(profile_user)
        has_sent = request.user.profile.has_sent_request_to(profile_user)
        is_following = request.user.profile.is_following(profile_user)

    # Check if viewer can see posts
    can_view_posts = True
    if profile.is_private and not is_me and not is_connected:
        can_view_posts = False

    posts = []
    if can_view_posts:
        posts = Post.objects.filter(author=profile_user).select_related('author', 'author__profile')

    mutual_conns = []
    if request.user.is_authenticated and not is_me:
        mutual_conns = request.user.profile.mutual_connections(profile_user)

    context = {
        'profile_user': profile_user,
        'profile': profile,
        'is_me': is_me,
        'is_connected': is_connected,
        'has_pending': has_pending,
        'has_sent': has_sent,
        'is_following': is_following,
        'can_view_posts': can_view_posts,
        'posts': posts,
        'mutual_connections': mutual_conns,
    }
    return render(request, 'profile/profile_detail.html', context)


@login_required
def edit_profile_view(request):
    profile = request.user.profile
    if request.method == 'POST':
        form = UserProfileForm(request.POST, request.FILES, instance=profile)
        if form.is_valid():
            form.save()
            # update User first/last name
            first_name = form.cleaned_data.get('first_name')
            last_name = form.cleaned_data.get('last_name')
            if first_name is not None:
                request.user.first_name = first_name
            if last_name is not None:
                request.user.last_name = last_name
            request.user.save()

            messages.success(request, "Your profile has been updated successfully!")
            return redirect('profile', username=request.user.username)
    else:
        initial = {
            'first_name': request.user.first_name,
            'last_name': request.user.last_name,
        }
        form = UserProfileForm(instance=profile, initial=initial)
    return render(request, 'profile/edit_profile.html', {'form': form})


@login_required
def deactivate_account_view(request):
    if request.method == 'POST':
        request.user.is_active = False
        request.user.save()
        logout(request)
        messages.info(request, "Your account has been deactivated. You can reactivate by contacting support.")
        return redirect('login')
    return render(request, 'profile/deactivate.html')


# -------------------------------------------------------------
# 4. Connect With People & Follow System
# -------------------------------------------------------------
@login_required
def network_view(request):
    user = request.user
    tab = request.GET.get('tab', 'discover')

    # Pending connection requests
    received_requests = Connection.objects.filter(receiver=user, status='pending').select_related('sender', 'sender__profile')
    sent_requests = Connection.objects.filter(sender=user, status='pending').select_related('receiver', 'receiver__profile')

    # Connected users
    accepted_connections = Connection.objects.filter(
        (Q(sender=user) | Q(receiver=user)), status='accepted'
    ).select_related('sender__profile', 'receiver__profile')

    connected_users = []
    for conn in accepted_connections:
        connected_users.append(conn.receiver if conn.sender == user else conn.sender)

    # Followers & Following
    followers = [f.follower for f in Follow.objects.filter(following=user).select_related('follower__profile')]
    following = [f.following for f in Follow.objects.filter(follower=user).select_related('following__profile')]

    # Discover people
    connected_ids = [u.id for u in connected_users]
    connected_ids.append(user.id)
    discover_people = User.objects.exclude(id__in=connected_ids).select_related('profile')[:20]

    context = {
        'tab': tab,
        'received_requests': received_requests,
        'sent_requests': sent_requests,
        'connected_users': connected_users,
        'followers': followers,
        'following': following,
        'discover_people': discover_people,
    }
    return render(request, 'network/network.html', context)


@login_required
@require_POST
def send_connection_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    if target != request.user:
        conn, created = Connection.objects.get_or_create(
            sender=request.user, receiver=target,
            defaults={'status': 'pending'}
        )
        if not created and conn.status == 'rejected':
            conn.status = 'pending'
            conn.save()

        create_notification(
            recipient=target,
            sender=request.user,
            notification_type='connection_request',
            text=f"{request.user.get_full_name() or request.user.username} sent you a connection request.",
            link="/network/?tab=requests"
        )
        messages.success(request, f"Connection request sent to {target.get_full_name() or target.username}.")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def accept_connection_view(request, connection_id):
    conn = get_object_or_404(Connection, id=connection_id, receiver=request.user)
    conn.status = 'accepted'
    conn.save()

    create_notification(
        recipient=conn.sender,
        sender=request.user,
        notification_type='connection_accepted',
        text=f"{request.user.get_full_name() or request.user.username} accepted your connection request.",
        link=f"/profile/{request.user.username}/"
    )
    messages.success(request, f"You are now connected with {conn.sender.get_full_name() or conn.sender.username}!")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def reject_connection_view(request, connection_id):
    conn = get_object_or_404(Connection, id=connection_id, receiver=request.user)
    conn.status = 'rejected'
    conn.save()
    messages.info(request, "Connection request declined.")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def remove_connection_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    Connection.objects.filter(
        (Q(sender=request.user, receiver=target) | Q(sender=target, receiver=request.user)),
        status='accepted'
    ).delete()
    messages.info(request, f"Connection removed with {target.username}.")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def toggle_follow_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    if target == request.user:
        return redirect('network')

    follow_rel = Follow.objects.filter(follower=request.user, following=target)
    if follow_rel.exists():
        follow_rel.delete()
        messages.info(request, f"You unfollowed {target.username}.")
    else:
        Follow.objects.create(follower=request.user, following=target)
        create_notification(
            recipient=target,
            sender=request.user,
            notification_type='follow',
            text=f"{request.user.get_full_name() or request.user.username} started following you.",
            link=f"/profile/{request.user.username}/"
        )
        messages.success(request, f"You are now following {target.username}!")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def block_user_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    if target != request.user:
        Block.objects.get_or_create(blocker=request.user, blocked=target)
        # remove connection / follow
        Connection.objects.filter(
            (Q(sender=request.user, receiver=target) | Q(sender=target, receiver=request.user))
        ).delete()
        Follow.objects.filter(
            (Q(follower=request.user, following=target) | Q(follower=target, following=request.user))
        ).delete()
        messages.warning(request, f"You blocked {target.username}.")
    return redirect('network')


@login_required
@require_POST
def unblock_user_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    Block.objects.filter(blocker=request.user, blocked=target).delete()
    messages.success(request, f"You unblocked {target.username}.")
    return redirect('network')


# -------------------------------------------------------------
# 5. Messaging / Chat System
# -------------------------------------------------------------
@login_required
def messages_view(request, username=None):
    user = request.user
    active_chat_user = None

    if username:
        active_chat_user = get_object_or_404(User.objects.select_related('profile'), username__iexact=username)
        # Mark unread messages from this user as read
        Message.objects.filter(sender=active_chat_user, receiver=user, is_read=False).update(is_read=True)

    # All conversations user participated in
    sent_receivers = Message.objects.filter(sender=user).values_list('receiver_id', flat=True)
    received_senders = Message.objects.filter(receiver=user).values_list('sender_id', flat=True)
    all_partner_ids = set(sent_receivers).union(set(received_senders))
    if active_chat_user:
        all_partner_ids.add(active_chat_user.id)

    conversations = []
    for pid in all_partner_ids:
        partner = User.objects.get(id=pid)
        last_msg = Message.objects.filter(
            (Q(sender=user, receiver=partner) | Q(sender=partner, receiver=user))
        ).order_by('-created_at').first()
        unread_count = Message.objects.filter(sender=partner, receiver=user, is_read=False).count()
        conversations.append({
            'partner': partner,
            'last_message': last_msg,
            'unread_count': unread_count,
        })

    conversations.sort(key=lambda c: c['last_message'].created_at if c['last_message'] else None, reverse=True)

    chat_messages = []
    if active_chat_user:
        chat_messages = Message.objects.filter(
            (Q(sender=user, receiver=active_chat_user) | Q(sender=active_chat_user, receiver=user))
        ).order_by('created_at')

    message_form = MessageForm()

    context = {
        'conversations': conversations,
        'active_chat_user': active_chat_user,
        'chat_messages': chat_messages,
        'message_form': message_form,
    }
    return render(request, 'chat/messages.html', context)


@login_required
@require_POST
def send_message_api(request, username):
    recipient = get_object_or_404(User, username__iexact=username)
    content = request.POST.get('content', '').strip()
    attachment = request.FILES.get('attachment')

    if content or attachment:
        msg = Message.objects.create(
            sender=request.user,
            receiver=recipient,
            content=content,
            attachment=attachment
        )
        create_notification(
            recipient=recipient,
            sender=request.user,
            notification_type='message',
            text=f"New message from {request.user.get_full_name() or request.user.username}.",
            link=f"/messages/{request.user.username}/"
        )
        return JsonResponse({
            'status': 'success',
            'id': msg.id,
            'content': msg.content,
            'created_at': msg.created_at.strftime('%H:%M'),
            'sender_username': request.user.username,
        })
    return JsonResponse({'status': 'error', 'message': 'Empty message'}, status=400)


@login_required
def get_messages_api(request, username):
    partner = get_object_or_404(User, username__iexact=username)
    last_id = int(request.GET.get('last_id', 0))

    new_messages = Message.objects.filter(
        (Q(sender=request.user, receiver=partner) | Q(sender=partner, receiver=request.user)),
        id__gt=last_id
    ).order_by('created_at')

    # Mark as read
    Message.objects.filter(sender=partner, receiver=request.user, is_read=False).update(is_read=True)

    data = []
    for m in new_messages:
        data.append({
            'id': m.id,
            'sender': m.sender.username,
            'is_me': m.sender == request.user,
            'content': m.content,
            'attachment_url': m.attachment.url if m.attachment else None,
            'time': m.created_at.strftime('%H:%M'),
        })
    return JsonResponse({'messages': data})


# -------------------------------------------------------------
# 6. Notifications
# -------------------------------------------------------------
@login_required
def notifications_view(request):
    notifications = Notification.objects.filter(recipient=request.user).select_related('sender', 'sender__profile', 'post')
    return render(request, 'notifications/notifications.html', {'notifications': notifications})


@login_required
def mark_notification_read_view(request, notification_id):
    notif = get_object_or_404(Notification, id=notification_id, recipient=request.user)
    notif.is_read = True
    notif.save()
    if notif.link:
        return redirect(notif.link)
    return redirect('notifications')


@login_required
@require_POST
def mark_all_notifications_read_view(request):
    Notification.objects.filter(recipient=request.user, is_read=False).update(is_read=True)
    messages.success(request, "All notifications marked as read.")
    return redirect('notifications')


# -------------------------------------------------------------
# 7. Global Search
# -------------------------------------------------------------
def search_view(request):
    query = request.GET.get('q', '').strip()
    category = request.GET.get('category', 'all')  # all, people, posts, groups, companies

    people = []
    posts = []
    groups = []
    companies = []

    if query:
        if category in ('all', 'people'):
            people = User.objects.filter(
                Q(username__icontains=query) |
                Q(first_name__icontains=query) |
                Q(last_name__icontains=query) |
                Q(profile__profession__icontains=query) |
                Q(profile__company_name__icontains=query) |
                Q(profile__skills__icontains=query)
            ).select_related('profile').distinct()[:20]

        if category in ('all', 'posts'):
            posts = Post.objects.filter(
                Q(content__icontains=query),
                visibility='public'
            ).select_related('author', 'author__profile')[:20]

        if category in ('all', 'groups'):
            groups = Group.objects.filter(
                Q(name__icontains=query) | Q(description__icontains=query)
            )[:15]

        if category in ('all', 'companies'):
            companies = CompanyPage.objects.filter(
                Q(name__icontains=query) | Q(tagline__icontains=query) | Q(industry__icontains=query)
            )[:15]

    context = {
        'query': query,
        'category': category,
        'people': people,
        'posts': posts,
        'groups': groups,
        'companies': companies,
        'total_results': len(people) + len(posts) + len(groups) + len(companies),
    }
    return render(request, 'search/search_results.html', context)


# -------------------------------------------------------------
# 8. Groups & Communities
# -------------------------------------------------------------
@login_required
def groups_list_view(request):
    all_groups = Group.objects.annotate(num_members=Count('members')).order_by('-num_members')
    my_groups = request.user.joined_groups.all()
    group_form = GroupForm()
    return render(request, 'groups/groups_list.html', {
        'all_groups': all_groups,
        'my_groups': my_groups,
        'group_form': group_form,
    })


@login_required
@require_POST
def create_group_view(request):
    form = GroupForm(request.POST, request.FILES)
    if form.is_valid():
        group = form.save(commit=False)
        group.creator = request.user
        group.save()
        group.members.add(request.user)
        messages.success(request, f"Community '{group.name}' created successfully!")
        return redirect('group_detail', slug=group.slug)
    messages.error(request, "Error creating community. Please check fields.")
    return redirect('groups_list')


@login_required
def group_detail_view(request, slug):
    group = get_object_or_404(Group.objects.prefetch_related('members'), slug=slug)
    is_member = group.members.filter(id=request.user.id).exists()
    is_creator = (group.creator == request.user)

    posts = Post.objects.filter(group=group).select_related('author', 'author__profile')
    post_form = PostForm()
    comment_form = CommentForm()

    return render(request, 'groups/group_detail.html', {
        'group': group,
        'is_member': is_member,
        'is_creator': is_creator,
        'posts': posts,
        'post_form': post_form,
        'comment_form': comment_form,
    })


@login_required
@require_POST
def join_group_view(request, slug):
    group = get_object_or_404(Group, slug=slug)
    if group.members.filter(id=request.user.id).exists():
        group.members.remove(request.user)
        messages.info(request, f"You left '{group.name}'.")
    else:
        group.members.add(request.user)
        messages.success(request, f"You joined '{group.name}'!")
    return redirect('group_detail', slug=slug)


# -------------------------------------------------------------
# 9. Company & Professional Pages
# -------------------------------------------------------------
@login_required
def companies_list_view(request):
    all_companies = CompanyPage.objects.annotate(num_followers=Count('followers')).order_by('-num_followers')
    my_companies = CompanyPage.objects.filter(owner=request.user)
    company_form = CompanyPageForm()
    return render(request, 'companies/companies_list.html', {
        'all_companies': all_companies,
        'my_companies': my_companies,
        'company_form': company_form,
    })


@login_required
@require_POST
def create_company_view(request):
    form = CompanyPageForm(request.POST, request.FILES)
    if form.is_valid():
        company = form.save(commit=False)
        company.owner = request.user
        company.save()
        company.followers.add(request.user)
        messages.success(request, f"Company Page '{company.name}' created successfully!")
        return redirect('company_detail', slug=company.slug)
    messages.error(request, "Error creating company page.")
    return redirect('companies_list')


@login_required
def company_detail_view(request, slug):
    company = get_object_or_404(CompanyPage.objects.prefetch_related('followers'), slug=slug)
    is_follower = company.followers.filter(id=request.user.id).exists()
    is_owner = (company.owner == request.user)

    posts = Post.objects.filter(company=company).select_related('author', 'author__profile')
    post_form = PostForm()
    comment_form = CommentForm()

    return render(request, 'companies/company_detail.html', {
        'company': company,
        'is_follower': is_follower,
        'is_owner': is_owner,
        'posts': posts,
        'post_form': post_form,
        'comment_form': comment_form,
    })


@login_required
@require_POST
def toggle_follow_company_view(request, slug):
    company = get_object_or_404(CompanyPage, slug=slug)
    if company.followers.filter(id=request.user.id).exists():
        company.followers.remove(request.user)
        messages.info(request, f"You unfollowed {company.name}.")
    else:
        company.followers.add(request.user)
        messages.success(request, f"You are now following {company.name}!")
    return redirect('company_detail', slug=slug)


# -------------------------------------------------------------
# 10. Admin Dashboard & Content Moderation
# -------------------------------------------------------------
@login_required
def admin_dashboard_view(request):
    if not request.user.is_staff:
        messages.error(request, "Access restricted to platform administrators.")
        return redirect('feed')

    total_users = User.objects.count()
    total_posts = Post.objects.count()
    total_comments = Comment.objects.count()
    total_groups = Group.objects.count()
    total_companies = CompanyPage.objects.count()
    pending_reports = ReportedContent.objects.filter(status='pending').select_related('reporter', 'post', 'reported_user')

    recent_users = User.objects.select_related('profile').order_by('-date_joined')[:15]
    recent_posts = Post.objects.select_related('author').order_by('-created_at')[:10]

    context = {
        'total_users': total_users,
        'total_posts': total_posts,
        'total_comments': total_comments,
        'total_groups': total_groups,
        'total_companies': total_companies,
        'pending_reports': pending_reports,
        'recent_users': recent_users,
        'recent_posts': recent_posts,
    }
    return render(request, 'admin_panel/dashboard.html', context)


@login_required
@require_POST
def report_content_view(request):
    post_id = request.POST.get('post_id')
    user_id = request.POST.get('user_id')
    reason = request.POST.get('reason', 'other')
    details = request.POST.get('details', '')

    post_obj = Post.objects.get(id=post_id) if post_id else None
    user_obj = User.objects.get(id=user_id) if user_id else None

    ReportedContent.objects.create(
        reporter=request.user,
        post=post_obj,
        reported_user=user_obj,
        reason=reason,
        details=details
    )
    messages.success(request, "Report submitted. Our moderation team will review this promptly.")
    return redirect(request.META.get('HTTP_REFERER', 'feed'))


@login_required
@require_POST
def admin_resolve_report_view(request, report_id):
    if not request.user.is_staff:
        return HttpResponseForbidden()
    report = get_object_or_404(ReportedContent, id=report_id)
    action = request.POST.get('action', 'dismiss')
    if action == 'delete_post' and report.post:
        report.post.delete()
        report.status = 'resolved'
    elif action == 'deactivate_user' and report.reported_user:
        report.reported_user.is_active = False
        report.reported_user.save()
        report.status = 'resolved'
    else:
        report.status = 'dismissed'
    report.save()
    messages.success(request, f"Report #{report.id} marked as {report.status}.")
    return redirect('admin_dashboard')


@login_required
@require_POST
def admin_toggle_user_status_view(request, user_id):
    if not request.user.is_staff:
        return HttpResponseForbidden()
    target = get_object_or_404(User, id=user_id)
    target.is_active = not target.is_active
    target.save()
    state = "activated" if target.is_active else "deactivated"
    messages.success(request, f"User {target.username} has been {state}.")
    return redirect('admin_dashboard')
