from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth import login, authenticate, logout
from django.contrib.auth.models import User
from django.contrib.auth.decorators import login_required, user_passes_test
from django.contrib import messages
from django.http import JsonResponse, HttpResponseForbidden
from django.db.models import Q, Count
from django.utils import timezone

from .models import (
    UserProfile, FriendRequest, Friendship, StatusPost, PostLike, PostComment,
    ScrapbookMessage, Community, CommunityMember, CommunityPost, CommunityComment,
    PhotoAlbum, Photo, PhotoComment, PrivateMessage, ProfileVisitor, Fan,
    Notification, Report
)
from .forms import (
    UserRegistrationForm, UserProfileForm, StatusPostForm, ScrapForm,
    CommunityForm, CommunityPostForm, PhotoAlbumForm, PhotoUploadForm, ReportForm
)

# ----------------- AUTHENTICATION -----------------

def register_view(request):
    if request.user.is_authenticated:
        return redirect('home')
    if request.method == 'POST':
        form = UserRegistrationForm(request.POST, request.FILES)
        if form.is_valid():
            user = form.save(commit=False)
            user.set_password(form.cleaned_data['password'])
            user.first_name = form.cleaned_data.get('full_name', '')
            user.save()

            # Profile
            profile, _ = UserProfile.objects.get_or_create(user=user)
            profile.full_name = form.cleaned_data.get('full_name', '')
            profile.mobile_number = form.cleaned_data.get('mobile_number', '')
            profile.gender = form.cleaned_data.get('gender', 'M')
            profile.date_of_birth = form.cleaned_data.get('date_of_birth')
            profile.city = form.cleaned_data.get('city', 'Mumbai')
            if 'profile_photo' in request.FILES:
                profile.profile_photo = request.FILES['profile_photo']
            profile.is_online = True
            profile.save()

            login(request, user)
            messages.success(request, f"Welcome to Orkut, {profile.full_name or user.username}! Your profile is ready.")
            return redirect('home')
    else:
        form = UserRegistrationForm()
    return render(request, 'auth/register.html', {'form': form})


def login_view(request):
    if request.user.is_authenticated:
        return redirect('home')
    if request.method == 'POST':
        login_input = request.POST.get('username', '').strip()
        password = request.POST.get('password', '')

        # Allow login via username or email
        user_obj = None
        if '@' in login_input:
            user_obj = User.objects.filter(email__iexact=login_input).first()
        else:
            user_obj = User.objects.filter(username__iexact=login_input).first()

        if user_obj:
            user = authenticate(request, username=user_obj.username, password=password)
            if user:
                profile, _ = UserProfile.objects.get_or_create(user=user)
                if profile.is_suspended:
                    messages.error(request, "Your Orkut account has been suspended by an administrator.")
                    return render(request, 'auth/login.html')
                login(request, user)
                profile.is_online = True
                profile.last_seen = timezone.now()
                profile.save()
                next_url = request.GET.get('next', 'home')
                return redirect(next_url)
        messages.error(request, "Invalid username/email or password.")
    return render(request, 'auth/login.html')


@login_required
def logout_view(request):
    if hasattr(request.user, 'orkut_profile'):
        request.user.orkut_profile.is_online = False
        request.user.orkut_profile.last_seen = timezone.now()
        request.user.orkut_profile.save()
    logout(request)
    messages.info(request, "You have been logged out of Orkut. See you soon!")
    return redirect('login')


def forgot_password_view(request):
    if request.method == 'POST':
        email = request.POST.get('email', '').strip()
        user = User.objects.filter(email__iexact=email).first()
        if user:
            messages.success(request, f"Password reset instructions have been generated for {email}.")
            return redirect('reset_password', username=user.username)
        else:
            messages.error(request, "No account found with this email address.")
    return render(request, 'auth/forgot_password.html')


def reset_password_view(request, username):
    user = get_object_or_404(User, username=username)
    if request.method == 'POST':
        new_pass = request.POST.get('password')
        conf_pass = request.POST.get('confirm_password')
        if new_pass and new_pass == conf_pass:
            user.set_password(new_pass)
            user.save()
            messages.success(request, "Password has been successfully updated! Please log in.")
            return redirect('login')
        else:
            messages.error(request, "Passwords do not match or are invalid.")
    return render(request, 'auth/reset_password.html', {'target_user': user})


# ----------------- HOME DASHBOARD -----------------

@login_required
def home_view(request):
    post_form = StatusPostForm()
    
    # Get friend IDs
    friendships = Friendship.objects.filter(Q(user1=request.user) | Q(user2=request.user))
    friend_ids = []
    for f in friendships:
        friend_ids.append(f.user2_id if f.user1_id == request.user.id else f.user1_id)
    
    # Posts: current user + friends
    posts = StatusPost.objects.filter(
        Q(author=request.user) | Q(author_id__in=friend_ids)
    ).select_related('author', 'author__orkut_profile').prefetch_related('likes', 'comments', 'comments__author', 'comments__author__orkut_profile')[:30]

    # Friend suggestions: users who are not friends, not pending request, and not self
    sent_reqs = FriendRequest.objects.filter(from_user=request.user).values_list('to_user_id', flat=True)
    recv_reqs = FriendRequest.objects.filter(to_user=request.user).values_list('from_user_id', flat=True)
    excluded_ids = set(friend_ids) | set(sent_reqs) | set(recv_reqs) | {request.user.id}
    
    suggestions = User.objects.exclude(id__in=excluded_ids).select_related('orkut_profile')[:6]

    # Recent profile visitors
    recent_visitors = ProfileVisitor.objects.filter(user=request.user).select_related('visitor', 'visitor__orkut_profile')[:6]

    # Total profile views
    profile = request.user.orkut_profile

    return render(request, 'dashboard/home.html', {
        'post_form': post_form,
        'posts': posts,
        'suggestions': suggestions,
        'recent_visitors': recent_visitors,
        'profile': profile,
    })


@login_required
def create_status_view(request):
    if request.method == 'POST':
        form = StatusPostForm(request.POST, request.FILES)
        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            post.save()
            messages.success(request, "Status updated successfully!")
    return redirect('home')


@login_required
def edit_status_view(request, post_id):
    post = get_object_or_404(StatusPost, id=post_id, author=request.user)
    if request.method == 'POST':
        form = StatusPostForm(request.POST, request.FILES, instance=post)
        if form.is_valid():
            form.save()
            messages.success(request, "Post updated!")
            return redirect('home')
    else:
        form = StatusPostForm(instance=post)
    return render(request, 'dashboard/edit_post.html', {'form': form, 'post': post})


@login_required
def delete_status_view(request, post_id):
    post = get_object_or_404(StatusPost, id=post_id)
    if post.author == request.user or request.user.is_staff:
        post.delete()
        messages.success(request, "Post deleted successfully.")
    else:
        messages.error(request, "You are not authorized to delete this post.")
    return redirect('home')


@login_required
def toggle_like_view(request, post_id):
    post = get_object_or_404(StatusPost, id=post_id)
    like_obj = PostLike.objects.filter(post=post, user=request.user).first()
    liked = False
    if like_obj:
        like_obj.delete()
        liked = False
    else:
        PostLike.objects.create(post=post, user=request.user)
        liked = True
        if post.author != request.user:
            Notification.objects.create(
                recipient=post.author,
                sender=request.user,
                notification_type='LIKE',
                title="Post Liked",
                message=f"{request.user.orkut_profile.full_name or request.user.username} liked your status update.",
                link='/#post-' + str(post.id)
            )
    if request.headers.get('x-requested-with') == 'XMLHttpRequest':
        return JsonResponse({'liked': liked, 'total_likes': post.likes.count()})
    return redirect('home')


@login_required
def add_comment_view(request, post_id):
    post = get_object_or_404(StatusPost, id=post_id)
    content = request.POST.get('content', '').strip()
    if content:
        PostComment.objects.create(post=post, author=request.user, content=content)
        if post.author != request.user:
            Notification.objects.create(
                recipient=post.author,
                sender=request.user,
                notification_type='COMMENT',
                title="New Comment",
                message=f"{request.user.orkut_profile.full_name or request.user.username} commented on your status.",
                link='/#post-' + str(post.id)
            )
        messages.success(request, "Comment added.")
    return redirect('home')


@login_required
def delete_comment_view(request, comment_id):
    comment = get_object_or_404(PostComment, id=comment_id)
    if comment.author == request.user or comment.post.author == request.user or request.user.is_staff:
        comment.delete()
        messages.success(request, "Comment deleted.")
    return redirect('home')


# ----------------- USER PROFILE -----------------

@login_required
def profile_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    profile, _ = UserProfile.objects.get_or_create(
        user=target_user,
        defaults={'full_name': target_user.get_full_name() or target_user.username}
    )

    # Track visitor and increment views if visiting someone else's profile
    if not is_self:
        profile.profile_views += 1
        profile.save()
        ProfileVisitor.objects.update_or_create(
            user=target_user,
            visitor=request.user,
            defaults={'visited_at': timezone.now()}
        )

    # Friendship status
    is_friend = False
    has_sent_request = False
    has_received_request = False
    if not is_self:
        is_friend = Friendship.objects.filter(
            Q(user1=request.user, user2=target_user) | Q(user1=target_user, user2=request.user)
        ).exists()
        if not is_friend:
            has_sent_request = FriendRequest.objects.filter(from_user=request.user, to_user=target_user, status='PENDING').exists()
            has_received_request = FriendRequest.objects.filter(from_user=target_user, to_user=request.user, status='PENDING').exists()

    # Is Fan
    is_fan = Fan.objects.filter(user=target_user, fan=request.user).exists()
    total_fans = Fan.objects.filter(user=target_user).count()

    # Friends of target user
    friendships = Friendship.objects.filter(Q(user1=target_user) | Q(user2=target_user)).select_related('user1', 'user2', 'user1__orkut_profile', 'user2__orkut_profile')
    friends = [f.user2 if f.user1 == target_user else f.user1 for f in friendships]

    # Mutual friends
    mutual_friends = []
    if not is_self:
        my_friendships = Friendship.objects.filter(Q(user1=request.user) | Q(user2=request.user))
        my_friend_ids = {f.user2_id if f.user1_id == request.user.id else f.user1_id for f in my_friendships}
        mutual_friends = [fr for fr in friends if fr.id in my_friend_ids]

    # Scraps
    scraps = ScrapbookMessage.objects.filter(recipient=target_user).select_related('sender', 'sender__orkut_profile')[:8]
    scrap_form = ScrapForm()

    # Photo albums
    albums = PhotoAlbum.objects.filter(user=target_user).prefetch_related('photos')[:4]

    # User's communities
    memberships = CommunityMember.objects.filter(user=target_user, status='ACTIVE').select_related('community')[:8]
    communities = [m.community for m in memberships]

    return render(request, 'profile/profile.html', {
        'target_user': target_user,
        'profile': profile,
        'is_self': is_self,
        'is_friend': is_friend,
        'has_sent_request': has_sent_request,
        'has_received_request': has_received_request,
        'is_fan': is_fan,
        'total_fans': total_fans,
        'friends': friends[:9],
        'total_friends': len(friends),
        'mutual_friends': mutual_friends,
        'scraps': scraps,
        'scrap_form': scrap_form,
        'albums': albums,
        'communities': communities,
    })


@login_required
def edit_profile_view(request):
    profile, _ = UserProfile.objects.get_or_create(user=request.user)
    if request.method == 'POST':
        form = UserProfileForm(request.POST, request.FILES, instance=profile)
        if form.is_valid():
            form.save()
            messages.success(request, "Your Orkut profile has been updated!")
            return redirect('profile', username=request.user.username)
    else:
        form = UserProfileForm(instance=profile)
    return render(request, 'profile/edit_profile.html', {'form': form})


@login_required
def rate_user_view(request, username):
    target_user = get_object_or_404(User, username=username)
    if target_user == request.user:
        messages.warning(request, "You cannot rate your own profile!")
        return redirect('profile', username=username)
    
    profile = target_user.orkut_profile
    if request.method == 'POST':
        trusty = int(request.POST.get('trusty', profile.trusty_rating))
        cool = int(request.POST.get('cool', profile.cool_rating))
        sexy = int(request.POST.get('sexy', profile.sexy_rating))

        profile.trusty_rating = min(3, max(1, trusty))
        profile.cool_rating = min(3, max(1, cool))
        profile.sexy_rating = min(3, max(1, sexy))
        profile.save()

        messages.success(request, f"You rated {profile.full_name or target_user.username}'s Trusty, Cool, and Sexy meter!")
    return redirect('profile', username=username)


@login_required
def settings_view(request):
    profile = request.user.orkut_profile
    if request.method == 'POST':
        action = request.POST.get('action')
        if action == 'fortune':
            profile.fortune = request.POST.get('fortune', profile.fortune)
            profile.save()
            messages.success(request, "Daily fortune status updated!")
        elif action == 'privacy':
            profile.scrapbook_privacy = request.POST.get('scrapbook_privacy', profile.scrapbook_privacy)
            profile.save()
            messages.success(request, "Privacy settings saved!")
        elif action == 'deactivate':
            profile.is_suspended = True
            profile.save()
            logout(request)
            messages.info(request, "Your account has been deactivated. You can log in anytime to reactivate it.")
            return redirect('login')
    return render(request, 'profile/settings.html', {'profile': profile})


# ----------------- SCRAPBOOK -----------------

@login_required
def scrapbook_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    profile = target_user.orkut_profile

    # Check privacy
    can_scrap = True
    if not is_self:
        if profile.scrapbook_privacy == 'ONLY_ME':
            can_scrap = False
        elif profile.scrapbook_privacy == 'FRIENDS':
            is_friend = Friendship.objects.filter(
                Q(user1=request.user, user2=target_user) | Q(user1=target_user, user2=request.user)
            ).exists()
            if not is_friend:
                can_scrap = False

    if request.method == 'POST':
        if not can_scrap:
            messages.error(request, "This user has restricted scrapbook messages.")
            return redirect('scrapbook', username=target_user.username)
        form = ScrapForm(request.POST)
        if form.is_valid():
            scrap = form.save(commit=False)
            scrap.sender = request.user
            scrap.recipient = target_user
            scrap.save()
            if target_user != request.user:
                Notification.objects.create(
                    recipient=target_user,
                    sender=request.user,
                    notification_type='SCRAP',
                    title="New Scrap Message",
                    message=f"{request.user.orkut_profile.full_name or request.user.username} left a scrap on your scrapbook!",
                    link=f"/scrapbook/{target_user.username}/"
                )
            messages.success(request, "Scrap message posted!")
            return redirect('scrapbook', username=target_user.username)
    else:
        form = ScrapForm()

    scraps = ScrapbookMessage.objects.filter(recipient=target_user).select_related('sender', 'sender__orkut_profile')

    return render(request, 'scrapbook/scrapbook.html', {
        'target_user': target_user,
        'profile': profile,
        'is_self': is_self,
        'scraps': scraps,
        'form': form,
        'can_scrap': can_scrap,
    })


@login_required
def delete_scrap_view(request, scrap_id):
    scrap = get_object_or_404(ScrapbookMessage, id=scrap_id)
    recipient_username = scrap.recipient.username
    if scrap.recipient == request.user or scrap.sender == request.user or request.user.is_staff:
        scrap.delete()
        messages.success(request, "Scrap removed.")
    return redirect('scrapbook', username=recipient_username)


# ----------------- FRIENDS MANAGEMENT -----------------

@login_required
def friends_list_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    friendships = Friendship.objects.filter(Q(user1=target_user) | Q(user2=target_user)).select_related('user1', 'user2', 'user1__orkut_profile', 'user2__orkut_profile')
    friends = [f.user2 if f.user1 == target_user else f.user1 for f in friendships]

    # Search filter within friends
    q = request.GET.get('q', '').strip()
    if q:
        friends = [f for f in friends if q.lower() in f.username.lower() or (f.orkut_profile.full_name and q.lower() in f.orkut_profile.full_name.lower())]

    return render(request, 'friends/friends_list.html', {
        'target_user': target_user,
        'is_self': is_self,
        'friends': friends,
        'total_friends': len(friends),
        'search_query': q,
    })


@login_required
def friend_requests_view(request):
    pending_requests = FriendRequest.objects.filter(to_user=request.user, status='PENDING').select_related('from_user', 'from_user__orkut_profile')
    sent_requests = FriendRequest.objects.filter(from_user=request.user, status='PENDING').select_related('to_user', 'to_user__orkut_profile')
    return render(request, 'friends/requests.html', {
        'pending_requests': pending_requests,
        'sent_requests': sent_requests,
    })


@login_required
def send_friend_request_view(request, username):
    target_user = get_object_or_404(User, username=username)
    if target_user == request.user:
        messages.warning(request, "You cannot send a friend request to yourself.")
        return redirect('profile', username=username)

    # Check if already friends
    already_friends = Friendship.objects.filter(
        Q(user1=request.user, user2=target_user) | Q(user1=target_user, user2=request.user)
    ).exists()
    if already_friends:
        messages.info(request, "You are already friends.")
        return redirect('profile', username=username)

    req, created = FriendRequest.objects.get_or_create(from_user=request.user, to_user=target_user)
    if created or req.status != 'PENDING':
        req.status = 'PENDING'
        req.save()
        Notification.objects.create(
            recipient=target_user,
            sender=request.user,
            notification_type='FRIEND_REQ',
            title="New Friend Request",
            message=f"{request.user.orkut_profile.full_name or request.user.username} sent you a friend request!",
            link='/friends/requests/'
        )
        messages.success(request, f"Friend request sent to {target_user.orkut_profile.full_name or target_user.username}!")
    else:
        messages.info(request, "Friend request is already pending.")
    return redirect('profile', username=username)


@login_required
def accept_friend_request_view(request, request_id):
    freq = get_object_or_404(FriendRequest, id=request_id, to_user=request.user, status='PENDING')
    freq.status = 'ACCEPTED'
    freq.save()

    # Create friendship
    Friendship.objects.get_or_create(user1=freq.from_user, user2=freq.to_user)
    
    Notification.objects.create(
        recipient=freq.from_user,
        sender=request.user,
        notification_type='FRIEND_ACC',
        title="Friend Request Accepted",
        message=f"{request.user.orkut_profile.full_name or request.user.username} accepted your friend request!",
        link=f"/profile/{request.user.username}/"
    )
    messages.success(request, f"You and {freq.from_user.username} are now friends on Orkut!")
    return redirect('friend_requests')


@login_required
def reject_friend_request_view(request, request_id):
    freq = get_object_or_404(FriendRequest, id=request_id, to_user=request.user)
    freq.status = 'REJECTED'
    freq.save()
    messages.info(request, "Friend request rejected.")
    return redirect('friend_requests')


@login_required
def remove_friend_view(request, username):
    target_user = get_object_or_404(User, username=username)
    Friendship.objects.filter(
        Q(user1=request.user, user2=target_user) | Q(user1=target_user, user2=request.user)
    ).delete()
    FriendRequest.objects.filter(
        Q(from_user=request.user, to_user=target_user) | Q(from_user=target_user, to_user=request.user)
    ).delete()
    messages.info(request, f"Removed {target_user.username} from your friends list.")
    return redirect('friends_list')


# ----------------- COMMUNITIES -----------------

@login_required
def communities_list_view(request):
    communities = Community.objects.annotate(member_count=Count('members')).order_by('-created_at')
    category = request.GET.get('category')
    q = request.GET.get('q')
    if category:
        communities = communities.filter(category__icontains=category)
    if q:
        communities = communities.filter(Q(name__icontains=q) | Q(description__icontains=q))

    # User's joined communities
    my_memberships = CommunityMember.objects.filter(user=request.user, status='ACTIVE').values_list('community_id', flat=True)

    return render(request, 'communities/communities_list.html', {
        'communities': communities,
        'my_community_ids': set(my_memberships),
        'search_query': q,
        'category_filter': category,
    })


@login_required
def create_community_view(request):
    if request.method == 'POST':
        form = CommunityForm(request.POST, request.FILES)
        if form.is_valid():
            comm = form.save(commit=False)
            comm.creator = request.user
            comm.save()
            CommunityMember.objects.create(community=comm, user=request.user, role='OWNER', status='ACTIVE')
            messages.success(request, f"Community '{comm.name}' created successfully!")
            return redirect('community_detail', community_id=comm.id)
    else:
        form = CommunityForm()
    return render(request, 'communities/create_community.html', {'form': form})


@login_required
def community_detail_view(request, community_id):
    comm = get_object_or_404(Community, id=community_id)
    membership = CommunityMember.objects.filter(community=comm, user=request.user).first()
    is_member = membership and membership.status == 'ACTIVE'
    is_pending = membership and membership.status == 'PENDING'
    is_admin = membership and membership.role in ('OWNER', 'MODERATOR')

    members = CommunityMember.objects.filter(community=comm, status='ACTIVE').select_related('user', 'user__orkut_profile')[:12]
    topics = CommunityPost.objects.filter(community=comm).select_related('author', 'author__orkut_profile').annotate(reply_count=Count('replies'))

    topic_form = CommunityPostForm()

    return render(request, 'communities/community_detail.html', {
        'community': comm,
        'membership': membership,
        'is_member': is_member,
        'is_pending': is_pending,
        'is_admin': is_admin,
        'members': members,
        'total_members': comm.members.filter(status='ACTIVE').count(),
        'topics': topics,
        'topic_form': topic_form,
    })


@login_required
def join_community_view(request, community_id):
    comm = get_object_or_404(Community, id=community_id)
    membership, created = CommunityMember.objects.get_or_create(community=comm, user=request.user)
    if comm.is_private:
        membership.status = 'PENDING'
        membership.save()
        messages.info(request, "Join request submitted. Awaiting community owner approval.")
    else:
        membership.status = 'ACTIVE'
        membership.save()
        messages.success(request, f"You are now a member of '{comm.name}'!")
    return redirect('community_detail', community_id=comm.id)


@login_required
def leave_community_view(request, community_id):
    comm = get_object_or_404(Community, id=community_id)
    if comm.creator == request.user:
        messages.error(request, "The community owner cannot leave the community. You can delete it instead.")
        return redirect('community_detail', community_id=comm.id)
    CommunityMember.objects.filter(community=comm, user=request.user).delete()
    messages.info(request, f"You left '{comm.name}'.")
    return redirect('communities_list')


@login_required
def create_community_topic_view(request, community_id):
    comm = get_object_or_404(Community, id=community_id)
    # Check if member
    membership = CommunityMember.objects.filter(community=comm, user=request.user, status='ACTIVE').first()
    if not membership:
        messages.error(request, "You must be a member to start a discussion in this community.")
        return redirect('community_detail', community_id=comm.id)

    if request.method == 'POST':
        form = CommunityPostForm(request.POST)
        if form.is_valid():
            post = form.save(commit=False)
            post.community = comm
            post.author = request.user
            post.save()
            messages.success(request, "Discussion topic created!")
    return redirect('community_detail', community_id=comm.id)


@login_required
def community_topic_detail_view(request, topic_id):
    topic = get_object_or_404(CommunityPost, id=topic_id)
    replies = CommunityComment.objects.filter(topic=topic).select_related('author', 'author__orkut_profile')

    if request.method == 'POST':
        content = request.POST.get('content', '').strip()
        if content:
            CommunityComment.objects.create(topic=topic, author=request.user, content=content)
            messages.success(request, "Reply posted!")
            return redirect('community_topic_detail', topic_id=topic.id)

    return render(request, 'communities/topic_detail.html', {
        'topic': topic,
        'community': topic.community,
        'replies': replies,
    })


# ----------------- PHOTOS & ALBUMS -----------------

@login_required
def photos_list_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    albums = PhotoAlbum.objects.filter(user=target_user).prefetch_related('photos')
    recent_photos = Photo.objects.filter(user=target_user).select_related('album')[:12]

    album_form = PhotoAlbumForm()
    upload_form = PhotoUploadForm()
    upload_form.fields['album'].queryset = PhotoAlbum.objects.filter(user=request.user)

    return render(request, 'photos/photos_list.html', {
        'target_user': target_user,
        'is_self': is_self,
        'albums': albums,
        'recent_photos': recent_photos,
        'album_form': album_form,
        'upload_form': upload_form,
    })


@login_required
def create_album_view(request):
    if request.method == 'POST':
        form = PhotoAlbumForm(request.POST)
        if form.is_valid():
            album = form.save(commit=False)
            album.user = request.user
            album.save()
            messages.success(request, f"Album '{album.title}' created!")
    return redirect('photos_list', username=request.user.username)


@login_required
def upload_photo_view(request):
    if request.method == 'POST':
        form = PhotoUploadForm(request.POST, request.FILES)
        form.fields['album'].queryset = PhotoAlbum.objects.filter(user=request.user)
        if form.is_valid():
            photo = form.save(commit=False)
            photo.user = request.user
            photo.save()
            messages.success(request, "Photo uploaded successfully!")
            return redirect('album_detail', album_id=photo.album.id)
    return redirect('photos_list', username=request.user.username)


@login_required
def album_detail_view(request, album_id):
    album = get_object_or_404(PhotoAlbum, id=album_id)
    photos = Photo.objects.filter(album=album).order_by('-created_at')
    is_owner = album.user == request.user

    upload_form = PhotoUploadForm(initial={'album': album})
    upload_form.fields['album'].queryset = PhotoAlbum.objects.filter(id=album.id)

    return render(request, 'photos/album_detail.html', {
        'album': album,
        'photos': photos,
        'is_owner': is_owner,
        'upload_form': upload_form,
    })


@login_required
def photo_detail_view(request, photo_id):
    photo = get_object_or_404(Photo, id=photo_id)
    comments = PhotoComment.objects.filter(photo=photo).select_related('author', 'author__orkut_profile')

    if request.method == 'POST':
        content = request.POST.get('content', '').strip()
        if content:
            PhotoComment.objects.create(photo=photo, author=request.user, content=content)
            messages.success(request, "Comment added.")
            return redirect('photo_detail', photo_id=photo.id)

    return render(request, 'photos/photo_detail.html', {
        'photo': photo,
        'album': photo.album,
        'comments': comments,
        'is_owner': photo.user == request.user,
    })


@login_required
def delete_photo_view(request, photo_id):
    photo = get_object_or_404(Photo, id=photo_id)
    album_id = photo.album.id
    if photo.user == request.user or request.user.is_staff:
        photo.delete()
        messages.success(request, "Photo deleted.")
    return redirect('album_detail', album_id=album_id)


# ----------------- PRIVATE MESSAGES / CHAT -----------------

@login_required
def messages_view(request, username=None):
    # Distinct chat partners
    sent_to = PrivateMessage.objects.filter(sender=request.user).values_list('recipient_id', flat=True)
    received_from = PrivateMessage.objects.filter(recipient=request.user).values_list('sender_id', flat=True)
    partner_ids = set(sent_to) | set(received_from)

    partners = User.objects.filter(id__in=partner_ids).select_related('orkut_profile')

    active_partner = None
    chat_messages = []
    if username:
        active_partner = get_object_or_404(User, username=username)
        # Mark as read
        PrivateMessage.objects.filter(sender=active_partner, recipient=request.user, is_read=False).update(is_read=True)
        # Fetch conversation
        chat_messages = PrivateMessage.objects.filter(
            (Q(sender=request.user, recipient=active_partner) | Q(sender=active_partner, recipient=request.user))
        ).order_by('created_at')

    return render(request, 'messages/messages.html', {
        'partners': partners,
        'active_partner': active_partner,
        'chat_messages': chat_messages,
    })


@login_required
def send_message_api(request):
    if request.method == 'POST':
        recipient_username = request.POST.get('recipient')
        msg_text = request.POST.get('message', '').strip()
        recipient = get_object_or_404(User, username=recipient_username)
        if msg_text:
            pm = PrivateMessage.objects.create(sender=request.user, recipient=recipient, message=msg_text)
            Notification.objects.create(
                recipient=recipient,
                sender=request.user,
                notification_type='MESSAGE',
                title="New Message",
                message=f"New message from {request.user.orkut_profile.full_name or request.user.username}",
                link=f"/messages/{request.user.username}/"
            )
            return JsonResponse({
                'status': 'ok',
                'id': pm.id,
                'message': pm.message,
                'sender': pm.sender.username,
                'time': pm.created_at.strftime('%I:%M %p')
            })
    return JsonResponse({'status': 'error'}, status=400)


@login_required
def get_messages_api(request, username):
    partner = get_object_or_404(User, username=username)
    # Mark as read
    PrivateMessage.objects.filter(sender=partner, recipient=request.user, is_read=False).update(is_read=True)
    
    msgs = PrivateMessage.objects.filter(
        (Q(sender=request.user, recipient=partner) | Q(sender=partner, recipient=request.user))
    ).order_by('created_at')

    data = [
        {
            'id': m.id,
            'sender': m.sender.username,
            'is_me': m.sender == request.user,
            'message': m.message,
            'time': m.created_at.strftime('%I:%M %p, %b %d')
        }
        for m in msgs
    ]
    return JsonResponse({'messages': data})


@login_required
def delete_conversation_view(request, username):
    partner = get_object_or_404(User, username=username)
    PrivateMessage.objects.filter(
        (Q(sender=request.user, recipient=partner) | Q(sender=partner, recipient=request.user))
    ).delete()
    messages.info(request, f"Conversation with {partner.username} deleted.")
    return redirect('messages')


# ----------------- FANS & PROFILE VISITORS -----------------

@login_required
def fans_list_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    # Users who made target_user their fan
    fans = Fan.objects.filter(user=target_user).select_related('fan', 'fan__orkut_profile')
    # Users target_user is a fan of (idols)
    idols = Fan.objects.filter(fan=target_user).select_related('user', 'user__orkut_profile')

    return render(request, 'fans/fans_list.html', {
        'target_user': target_user,
        'is_self': is_self,
        'fans': fans,
        'idols': idols,
    })


@login_required
def toggle_fan_view(request, username):
    target_user = get_object_or_404(User, username=username)
    if target_user == request.user:
        messages.warning(request, "You cannot be your own fan!")
        return redirect('profile', username=username)

    fan_obj = Fan.objects.filter(user=target_user, fan=request.user).first()
    if fan_obj:
        fan_obj.delete()
        messages.info(request, f"You are no longer a fan of {target_user.orkut_profile.full_name or target_user.username}.")
    else:
        Fan.objects.create(user=target_user, fan=request.user)
        Notification.objects.create(
            recipient=target_user,
            sender=request.user,
            notification_type='FAN',
            title="New Fan!",
            message=f"{request.user.orkut_profile.full_name or request.user.username} became your fan!",
            link=f"/fans/{target_user.username}/"
        )
        messages.success(request, f"You are now a fan of {target_user.orkut_profile.full_name or target_user.username}! ❤️")
    return redirect('profile', username=username)


@login_required
def profile_visitors_view(request):
    visitors = ProfileVisitor.objects.filter(user=request.user).select_related('visitor', 'visitor__orkut_profile')
    return render(request, 'fans/visitors.html', {
        'visitors': visitors,
        'total_views': request.user.orkut_profile.profile_views,
    })


# ----------------- NOTIFICATIONS -----------------

@login_required
def notifications_view(request):
    notifs = Notification.objects.filter(recipient=request.user).select_related('sender', 'sender__orkut_profile')
    return render(request, 'dashboard/notifications.html', {'notifications': notifs})


@login_required
def mark_notification_read_view(request, notification_id):
    notif = get_object_or_404(Notification, id=notification_id, recipient=request.user)
    notif.is_read = True
    notif.save()
    return redirect(notif.link or 'notifications')


@login_required
def mark_all_notifications_read_view(request):
    Notification.objects.filter(recipient=request.user, is_read=False).update(is_read=True)
    messages.success(request, "All notifications marked as read.")
    return redirect('notifications')


# ----------------- SEARCH -----------------

@login_required
def search_view(request):
    q = request.GET.get('q', '').strip()
    category = request.GET.get('cat', 'all')
    users = []
    communities = []
    posts = []

    if q:
        if category in ('all', 'people'):
            users = User.objects.filter(
                Q(username__icontains=q) |
                Q(orkut_profile__full_name__icontains=q) |
                Q(orkut_profile__city__icontains=q) |
                Q(orkut_profile__profession__icontains=q)
            ).select_related('orkut_profile').distinct()[:15]

        if category in ('all', 'communities'):
            communities = Community.objects.filter(
                Q(name__icontains=q) | Q(description__icontains=q)
            ).annotate(member_count=Count('members'))[:15]

        if category in ('all', 'posts'):
            posts = StatusPost.objects.filter(
                Q(content__icontains=q)
            ).select_related('author', 'author__orkut_profile')[:15]

    return render(request, 'dashboard/search_results.html', {
        'query': q,
        'category': category,
        'users': users,
        'communities': communities,
        'posts': posts,
    })


# ----------------- CONTENT REPORTING -----------------

@login_required
def report_content_view(request):
    if request.method == 'POST':
        content_type = request.POST.get('content_type')
        content_id = request.POST.get('content_id')
        reason = request.POST.get('reason', '').strip()
        if content_type and content_id and reason:
            Report.objects.create(
                reported_by=request.user,
                content_type=content_type,
                content_id=content_id,
                reason=reason
            )
            messages.success(request, "Report submitted. Our moderation team will review this shortly.")
    return redirect('home')


# ----------------- ADMIN DASHBOARD -----------------

@login_required
def admin_dashboard_view(request):
    if not request.user.is_staff and not request.user.is_superuser:
        messages.error(request, "Administrator privileges required.")
        return redirect('home')

    total_users = User.objects.count()
    total_posts = StatusPost.objects.count()
    total_communities = Community.objects.count()
    total_photos = Photo.objects.count()
    total_scraps = ScrapbookMessage.objects.count()

    users_list = User.objects.all().select_related('orkut_profile').order_by('-date_joined')[:20]
    reports = Report.objects.filter(status='PENDING').select_related('reported_by')[:20]

    return render(request, 'admin_panel/dashboard.html', {
        'total_users': total_users,
        'total_posts': total_posts,
        'total_communities': total_communities,
        'total_photos': total_photos,
        'total_scraps': total_scraps,
        'users_list': users_list,
        'reports': reports,
    })


@login_required
def admin_toggle_user_status(request, user_id):
    if not request.user.is_staff:
        return HttpResponseForbidden()
    target_user = get_object_or_404(User, id=user_id)
    profile = target_user.orkut_profile
    profile.is_suspended = not profile.is_suspended
    profile.save()
    status_str = "Suspended" if profile.is_suspended else "Reactivated"
    messages.info(request, f"User {target_user.username} is now {status_str}.")
    return redirect('admin_dashboard')


@login_required
def admin_resolve_report(request, report_id, action):
    if not request.user.is_staff:
        return HttpResponseForbidden()
    report = get_object_or_404(Report, id=report_id)
    if action == 'resolve':
        report.status = 'RESOLVED'
        # Delete reported content if applicable
        if report.content_type == 'post':
            StatusPost.objects.filter(id=report.content_id).delete()
        elif report.content_type == 'scrap':
            ScrapbookMessage.objects.filter(id=report.content_id).delete()
        elif report.content_type == 'photo':
            Photo.objects.filter(id=report.content_id).delete()
        messages.success(request, "Report resolved and violating content removed.")
    else:
        report.status = 'DISMISSED'
        messages.info(request, "Report dismissed.")
    report.save()
    return redirect('admin_dashboard')
